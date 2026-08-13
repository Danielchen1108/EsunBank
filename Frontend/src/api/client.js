/**
 * 後端 API 用戶端。
 *
 * 以 fetch 實作，不額外引入 HTTP 函式庫——題目未要求，依 SCOPE-BOUNDARY.md
 * 的判定原則 R-3（題目未提及則不做）。
 *
 * 路徑一律以 /api 開頭，由 vite.config.js 的 proxy 轉發至 Application Server。
 */

/**
 * 發送請求並解析 JSON 回應。
 *
 * @param {string} path 以 /api 開頭的路徑
 * @param {RequestInit} [options] fetch 選項
 * @returns {Promise<any>} 解析後的回應內容
 * @throws {Error} HTTP 狀態非 2xx 時拋出
 */
export async function request(path, options = {}) {
  const response = await fetch(path, {
    headers: { 'Content-Type': 'application/json', ...options.headers },
    ...options,
  })

  if (!response.ok) {
    throw new Error(`${response.status} ${response.statusText}`)
  }

  return response.json()
}

/** 健康檢查：確認 Application Server 與資料庫是否連通（R101 骨架驗證用）。 */
export function fetchHealth() {
  return request('/api/health')
}
