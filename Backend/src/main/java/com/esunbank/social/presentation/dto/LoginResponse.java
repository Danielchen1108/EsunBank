package com.esunbank.social.presentation.dto;

/**
 * 登入成功回應。
 *
 * <p>不含使用者名稱、Email 等欄位——需求的登入功能只需證明身分。
 *
 * <p>刻意不回傳有效期或更新用憑證：需求未提及，後來決定不實作
 *
 * @param userId 登入成功的使用者 ID
 * @param token  JWT 憑證。後續請求須以 {@code Authorization: Bearer <token>} 帶回，
 *               否則發文與留言端點會回 401
 */
public record LoginResponse(Long userId, String userName, String token) {
}
