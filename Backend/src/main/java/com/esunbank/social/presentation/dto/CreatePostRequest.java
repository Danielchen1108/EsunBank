package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增發文請求（題目 §3）。
 *
 * <p><b>不含 userId：</b>發文者取自登入憑證（{@code AuthenticatedUser}），不由請求指定——
 * 否則使用者可宣稱自己是任何人，題目 §2 的驗證要求會被繞過。
 *
 * <p>內容長度上限 2000 與 {@code post.content VARCHAR(2000)} 一致（ADR-005）。
 * 在此先擋下，可讓超長輸入得到可讀的 400 而非資料庫層的 500。
 *
 * @param content 發文內容，必填，最長 2000 字元
 * @param image   圖片路徑，可不填。題目第 2 頁標示 Image 為非必要欄位；
 *                此處僅接受路徑字串，<b>不含上傳功能</b>（`SCOPE-BOUNDARY.md` Out of Scope）
 */
public record CreatePostRequest(

        @NotBlank(message = "發文內容為必填")
        @Size(max = 2000, message = "發文內容不可超過 2000 字")
        String content,

        @Size(max = 255, message = "圖片路徑不可超過 255 字")
        String image) {
}
