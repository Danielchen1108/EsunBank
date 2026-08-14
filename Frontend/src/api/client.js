/**
 * 後端 API 用戶端。
 *
 * 以 fetch 實作，不額外引入 HTTP 函式庫——題目未要求，依 SCOPE-BOUNDARY.md
 * 的判定原則 R-3（題目未提及則不做）。
 *
 * 路徑一律以 /api 開頭，由 vite.config.js 的 proxy 轉發至 Application Server。
 */

import { ref } from 'vue'

/** 帶有 HTTP 狀態與回應內容的錯誤，供呼叫端區分 400／401／409 等情境。 */
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/**
 * 登入憑證的存放鍵。
 *
 * 存在 localStorage 而非 sessionStorage：後端刻意不實作憑證有效期
 * （ADR-003 / SCOPE-BOUNDARY.md），憑證本身長期有效，
 * 用 sessionStorage 會讓「關掉分頁就得重登入」這件事看起來像有效期，語意不符。
 *
 * 不使用 Cookie：後端的過濾鏈以 Authorization 標頭驗證且停用 CSRF 防護，
 * 改用 Cookie 會讓請求自動附帶憑證，反而重新引入 CSRF 的攻擊前提。
 */
const TOKEN_KEY = 'esunbank.social.token'

/**
 * 目前登入者的顯示名稱。
 *
 * 與憑證分開存放：token 是給後端看的，這個是給畫面看的。
 * 不從 JWT 解出來——那會讓前端依賴憑證的內部格式，
 * 換簽章方式或調整 claim 就會壞掉。登入回應直接給名稱，來源明確。
 */
const USER_NAME_KEY = 'esunbank.social.userName'

/**
 * 登入狀態的響應式來源。
 *
 * localStorage 讀取不是響應式的——導覽列無法得知「剛剛登出了」。
 * 以一個 ref 作為單一事實來源，login()／logout() 負責同步，
 * 畫面只讀這裡，不各自去翻 localStorage。
 *
 * 初值取自 localStorage：重新整理後仍要記得使用者是誰。
 */
export const authState = ref({
  loggedIn: localStorage.getItem(TOKEN_KEY) !== null,
  userName: localStorage.getItem(USER_NAME_KEY),
})

/** 目前的登入憑證；未登入時為 null。 */
export function authToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 是否已登入——供畫面判斷要顯示登入表單還是已登入狀態。 */
export function isLoggedIn() {
  return authToken() !== null
}

/** 目前登入者的顯示名稱；未登入時為 null。 */
export function currentUserName() {
  return localStorage.getItem(USER_NAME_KEY)
}

/**
 * 登出。
 *
 * **題目未要求登出功能**（§1–§4 只有註冊、登入、發文、留言），
 * 為了讓登入流程有出口而追加——有閘門卻沒有出口，使用者只能靠清除瀏覽器資料脫身。
 * 記於 SCOPE-BOUNDARY.md 的「Owner 追加」。
 *
 * 純前端動作：後端刻意不實作憑證有效期與撤銷（ADR-003），
 * 所以這裡只是丟掉本機憑證，不是讓伺服器端失效。**這個限制必須誠實看待**——
 * 憑證若在登出前外流，刪掉本機副本並不能讓它失效。
 */
export function logout() {
  localStorage.removeItem(TOKEN_KEY)
  localStorage.removeItem(USER_NAME_KEY)
  authState.value = { loggedIn: false, userName: null }
}

/**
 * 發送請求並解析 JSON 回應。
 *
 * 已登入時自動帶上 `Authorization: Bearer <token>`——題目 §2 要求
 * 只有登入的使用者可以發文或留言，後端據此標頭判斷身分。
 * 未登入時不帶，讓註冊與登入等公開端點照常運作。
 *
 * @param {string} path 以 /api 開頭的路徑
 * @param {RequestInit} [options] fetch 選項
 * @returns {Promise<any>} 解析後的回應內容
 * @throws {ApiError} HTTP 狀態非 2xx 時拋出
 */
export async function request(path, options = {}) {
  const token = authToken()

  const response = await fetch(path, {
    headers: {
      'Content-Type': 'application/json',
      ...(token ? { Authorization: `Bearer ${token}` } : {}),
      ...options.headers,
    },
    ...options,
  })

  const body = await response.json().catch(() => null)

  if (!response.ok) {
    throw new ApiError(response.status, body)
  }

  return body
}

/**
 * 註冊帳號（題目 §1）。
 *
 * @param {{phone: string, userName: string, email: string, password: string, biography?: string}} payload
 * @returns {Promise<{userId: number}>}
 */
export function register(payload) {
  return request('/api/auth/register', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * 登入（題目 §2）。
 *
 * 成功後把憑證存起來，後續請求由 request() 自動帶上。
 *
 * 刻意沒有對應的 logout()：題目未提及登出功能，依 SCOPE-BOUNDARY.md
 * 的判定原則 R-3 不實作。
 *
 * @param {{phone: string, password: string}} payload
 * @returns {Promise<{userId: number, userName: string, token: string}>}
 */
export async function login(payload) {
  // 換帳號登入前先清掉舊憑證，避免登入失敗時畫面仍顯示上一個人的身分
  logout()

  const result = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

  localStorage.setItem(TOKEN_KEY, result.token)
  localStorage.setItem(USER_NAME_KEY, result.userName)
  authState.value = { loggedIn: true, userName: result.userName }

  return result
}

/**
 * 列出所有發文（題目 §3）。
 *
 * 排序由 sp_post_list 決定：由新到舊（created_at DESC，同秒時以 post_id DESC 決勝）。
 * 前端照回傳順序顯示、不重排——排序規則只寫在 SQL 一個地方。
 *
 * 已軟刪除的發文不會出現（過濾條件在 Stored Procedure 內，ADR-004）。
 *
 * 每篇發文一併帶回自己的留言：留言的讀取沒有獨立端點，全部走這一支——
 * 若改成每篇再打一次 API，列表一展開就是 N+1 次請求。
 * comments 由後端依時間由舊到新排序（與發文相反，見 PostsView.vue 的說明），
 * 沒有留言時為空陣列。
 *
 * @returns {Promise<Array<{postId: number, userId: number, userName: string,
 *   content: string, createdAt: string,
 *   comments: Array<{commentId: number, userId: number, userName: string,
 *     content: string, createdAt: string}>}>>}
 */
export function listPosts() {
  return request('/api/posts')
}

/**
 * 新增發文（題目 §3）。
 *
 * 只送 content：發文者由後端從登入憑證取得，不由前端指定——
 * 否則任何人都能宣稱自己是別人（題目 §2）。
 *
 * 沒有 image：post.image 欄位保留於資料庫（題目第 2 頁），但題目沒有上傳功能，
 * API 不開放填寫（F004-API.md § image 不在 API 契約內）。
 *
 * @param {{content: string}} payload
 * @returns {Promise<{postId: number}>}
 */
export function createPost(payload) {
  return request('/api/posts', {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}

/**
 * 編輯發文（題目 §3）。
 *
 * 後端刻意不檢查操作者是否為發文者（F004-API.md § BG-4 裁決），
 * 故任何登入者都能編輯任何一篇發文。前端不另外擋——授權判定在後端，
 * 前端擋畫面既不是安全機制，也會與後端的實際行為不一致。
 *
 * @param {number} postId
 * @param {{content: string}} payload
 * @returns {Promise<{postId: number, userId: number, userName: string,
 *   content: string, createdAt: string}>}
 */
export function updatePost(postId, payload) {
  return request(`/api/posts/${postId}`, {
    method: 'PUT',
    body: JSON.stringify(payload),
  })
}

/**
 * 刪除發文（題目 §3）。
 *
 * 後端為軟刪除，且於同一交易內連動標記該發文的留言（ADR-004）——
 * 這是題目 §6 Transaction 要求的落地點。前端只需知道成功即代表兩者都已標記。
 *
 * 成功時後端回 204 No Content，沒有回應內容，故 request() 解析後為 null。
 *
 * @param {number} postId
 * @returns {Promise<null>}
 */
export function deletePost(postId) {
  return request(`/api/posts/${postId}`, {
    method: 'DELETE',
  })
}

/**
 * 針對發文新增留言（題目 §4）。
 *
 * postId 放在路徑而非請求內容：留言依附於發文，歸屬關係直接呈現在 URI 上
 * （RESTful，題目 §6）。留言者同樣由後端從登入憑證取得。
 *
 * 沒有 listComments：留言的讀取不走獨立端點，由 listPosts() 隨發文一併帶回。
 *
 * 刻意沒有 updateComment / deleteComment：題目 §4 只寫「新增留言」，
 * 依 SCOPE-BOUNDARY.md 判定原則 R-3 不實作，後端也沒有對應端點。
 *
 * @param {number} postId 目標發文，必須存在且未被刪除，否則後端回 404
 * @param {{content: string}} payload
 * @returns {Promise<{commentId: number}>}
 */
export function createComment(postId, payload) {
  return request(`/api/posts/${postId}/comments`, {
    method: 'POST',
    body: JSON.stringify(payload),
  })
}
