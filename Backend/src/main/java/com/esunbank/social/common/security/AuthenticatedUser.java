package com.esunbank.social.common.security;

/**
 * 已通過驗證的使用者（共用層）。
 *
 * <p>用來確保只有登入的使用者可以發文或留言。
 *
 * <p>本型別是 Spring Security 的 {@code Authentication} principal，
 * 由 JWT 過濾器在驗證成功後放入 {@code SecurityContext}。
 *
 * <p><b>各功能取得目前使用者的統一方式：</b>
 *
 * <pre>{@code
 * @PostMapping("/api/posts")
 * public ResponseEntity<?> create(
 *         @AuthenticationPrincipal AuthenticatedUser currentUser,
 *         @Valid @RequestBody CreatePostRequest request) {
 *     ...使用 currentUser.userId()...
 * }
 * }</pre>
 *
 * <p>此為 ／／之間的共用契約，先於各功能實作定義，
 * 使發文與留言不必等待驗證機制完成即可開發。
 *
 * @param userId 使用者 ID，對應 {@code user.user_id}
 * @param phone  手機號碼，對應 {@code user.phone}（登入帳號）
 */
public record AuthenticatedUser(Long userId, String phone) {
}
