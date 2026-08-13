package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 編輯發文請求（題目 §3）。
 *
 * <p>可修改的欄位只有 content。{@code userId} 與 {@code createdAt} 不開放修改：
 * 改寫作者或發佈時間屬於竄改而非編輯。
 *
 * <p><b>不含 image：</b>與 {@link CreatePostRequest} 同一裁決——欄位保留於 schema
 * （題目第 2 頁），但無上傳功能故 API 不開放（`SCOPE-BOUNDARY.md` R-3）。
 *
 * <p>驗證規則與新增相同（ADR-005），避免同一欄位在兩條路徑上有不同上限。
 *
 * @param content 新的發文內容，必填，最長 2000 字元
 */
public record UpdatePostRequest(

        @NotBlank(message = "發文內容為必填")
        @Size(max = 2000, message = "發文內容不可超過 2000 字")
        String content) {
}
