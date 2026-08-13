/**
 * 後端 API 用戶端。
 *
 * 以 fetch 實作，不額外引入 HTTP 函式庫——題目未要求，依 SCOPE-BOUNDARY.md
 * 的判定原則 R-3（題目未提及則不做）。
 *
 * 路徑一律以 /api 開頭，由 vite.config.js 的 proxy 轉發至 Application Server。
 */

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

/** 目前的登入憑證；未登入時為 null。 */
export function authToken() {
  return localStorage.getItem(TOKEN_KEY)
}

/** 是否已登入——供畫面判斷要顯示登入表單還是已登入狀態。 */
export function isLoggedIn() {
  return authToken() !== null
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

/** 健康檢查：確認 Application Server 與資料庫是否連通（R101 骨架驗證用）。 */
export function fetchHealth() {
  return request('/api/health')
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
 * @returns {Promise<{userId: number, token: string}>}
 */
export async function login(payload) {
  const result = await request('/api/auth/login', {
    method: 'POST',
    body: JSON.stringify(payload),
  })

  localStorage.setItem(TOKEN_KEY, result.token)

  return result
}

/**
 * 列出所有發文（題目 §3）。
 *
 * 後端 sp_post_list 刻意沒有 ORDER BY——題目未定義排序規則
 * （SCOPE-BOUNDARY.md 列為 Out of Scope）。前端照回傳順序顯示，
 * 不自行排序，避免實作出題目未定義的行為。
 *
 * 已軟刪除的發文不會出現（過濾條件在 Stored Procedure 內，ADR-004）。
 *
 * @returns {Promise<Array<{postId: number, userId: number, userName: string,
 *   content: string, createdAt: string}>>}
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
 * 刻意沒有 listComments / updateComment / deleteComment：題目 §4 只寫「新增留言」，
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
