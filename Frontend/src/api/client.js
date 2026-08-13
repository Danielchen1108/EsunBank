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
