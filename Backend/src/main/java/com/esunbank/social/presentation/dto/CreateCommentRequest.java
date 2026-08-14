package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 新增留言請求。
 *
 * <p>只有一個欄位。留言者取自已驗證身分、目標發文取自 URI 路徑，
 * 兩者皆<b>刻意不放進請求主體</b>——若可由用戶端指定，任何人都能冒用他人身分
 * 留言。
 *
 * <p><b>刻意不驗證</b>留言內容中的 HTML 或指令碼：XSS 的防護位置在輸出端
 * （Vue 插值自動跳脫），寫入端改寫會造成資料失真。見
 *
 * @param content 留言內容。最長 500 字，對應 {@code comment.content VARCHAR(500)}
 */
public record CreateCommentRequest(

        @NotBlank(message = "留言內容為必填")
        @Size(max = 500, message = "留言內容不可超過 500 字")
        String content) {
}
