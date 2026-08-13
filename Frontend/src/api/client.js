/**
 * 後端 API 用戶端。
 *
 * 以 fetch 實作，不額外引入 HTTP 函式庫——題目未要求，依 SCOPE-BOUNDARY.md
 * 的判定原則 R-3（題目未提及則不做）。
 *
 * 路徑一律以 /api 開頭，由 vite.config.js 的 proxy 轉發至 Application Server。
 */

/** 帶有 HTTP 狀態與回應內容的錯誤，供呼叫端區分 400／409 等情境。 */
export class ApiError extends Error {
  constructor(status, body) {
    super(body?.message ?? `HTTP ${status}`)
    this.name = 'ApiError'
    this.status = status
    this.body = body
  }
}

/**
 * 發送請求並解析 JSON 回應。
 *
 * @param {string} path 以 /api 開頭的路徑
 * @param {RequestInit} [options] fetch 選項
 * @returns {Promise<any>} 解析後的回應內容
 * @throws {ApiError} HTTP 狀態非 2xx 時拋出
 */
export async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
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
