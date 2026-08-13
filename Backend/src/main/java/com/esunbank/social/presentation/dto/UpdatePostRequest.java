package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 編輯發文請求（題目 §3）。
 *
 * <p>可修改的欄位為 content 與 image（{@code F004-REQ.md} OQ-4 的決定）——
 * 這也是 {@code sp_post_update} 的參數範圍。{@code userId} 與 {@code createdAt}
 * 不開放修改：改寫作者或發佈時間屬於竄改而非編輯。
 *
 * <p><b>PUT 為整體取代語意：</b>未帶 image 即視為清空該欄位。若要保留原圖，
 * 請於請求中原樣帶回。
 *
 * <p>驗證規則與新增相同（ADR-005），避免同一欄位在兩條路徑上有不同上限。
 *
 * @param content 新的發文內容，必填，最長 2000 字元
 * @param image   新的圖片路徑，可不填
 */
public record UpdatePostRequest(

        @NotBlank(message = "發文內容為必填")
        @Size(max = 2000, message = "發文內容不可超過 2000 字")
        String content,

        @Size(max = 255, message = "圖片路徑不可超過 255 字")
        String image) {
}
