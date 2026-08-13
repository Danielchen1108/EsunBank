package com.esunbank.social.presentation.dto;

/**
 * 登入成功回應。
 *
 * <p>不含使用者名稱、Email 等欄位——題目 §2 的登入功能只需證明身分。
 *
 * <p>刻意不回傳有效期或更新用憑證：題目未提及，owner 裁決不實作
 * （ADR-003、{@code SCOPE-BOUNDARY.md} Out of Scope）。
 *
 * @param userId 登入成功的使用者 ID
 * @param token  JWT 憑證。後續請求須以 {@code Authorization: Bearer <token>} 帶回，
 *               否則發文與留言端點會回 401（題目 §2）
 */
public record LoginResponse(Long userId, String token) {
}
