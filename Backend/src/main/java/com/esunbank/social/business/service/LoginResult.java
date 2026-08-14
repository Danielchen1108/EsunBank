package com.esunbank.social.business.service;

/**
 * 登入結果（業務層輸出）。
 *
 * <p>不含使用者名稱、Email 等其他欄位——需求 §2 的登入功能只需證明身分，
 * 回傳額外 PII 沒有對應的需求。
 *
 * @param userId 登入成功的使用者 ID
 * @param token  JWT 憑證。後續請求以 {@code Authorization: Bearer <token>} 帶回
 */
public record LoginResult(Long userId, String userName, String token) {
}
