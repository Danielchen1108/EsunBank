package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增發文請求。
 *
 * <p><b>不含 userId：</b>發文者取自登入憑證（{@code AuthenticatedUser}），不由請求指定——
 * 否則使用者可宣稱自己是任何人，需求的驗證要求會被繞過。
 *
 * <p>內容長度上限 2000 與 {@code post.content VARCHAR(2000)} 一致。
 * 在此先擋下，可讓超長輸入得到可讀的 400 而非資料庫層的 500。
 *
 * <p><b>不含 image：</b>{@code post.image} 欄位保留於 schema（需求規格列有 Image，
 * 標示為非必要欄位），但需求的功能清單沒有任何上傳功能，
 * 依「沒寫就不用」<b>API 不開放填寫</b>。
 * 沒有上傳端點時開放這個欄位，等於讓用戶端送一個沒有來源的路徑字串——
 * 寫得進去，卻永遠指不到任何檔案。
 *
 * @param content 發文內容，必填，最長 2000 字元
 */
public record CreatePostRequest(

        @NotBlank(message = "發文內容為必填")
        @Size(max = 2000, message = "發文內容不可超過 2000 字")
        String content) {
}
