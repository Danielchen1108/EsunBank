package com.esunbank.social.business.service;

/**
 * 新增發文的業務指令（業務層）。
 *
 * <p>與 {@code CreatePostRequest} 分開的理由：{@code userId} 不來自請求內容，
 * 而是由展示層從登入憑證取出後填入。若讓請求 DTO 直接帶 {@code userId}，
 * 使用者就能宣稱自己是任何人——題目 §2 的驗證要求會被繞過。
 *
 * <p><b>不含 image：</b>API 不開放填寫圖片路徑（無上傳功能，`SCOPE-BOUNDARY.md` R-3）；
 * {@code sp_post_create} 的 image 參數由資料層固定傳 null。
 *
 * @param userId  發文者，取自 {@code AuthenticatedUser.userId()}
 * @param content 發文內容
 */
public record PostCreateCommand(Long userId, String content) {
}
