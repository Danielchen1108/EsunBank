package com.esunbank.social.business.service;

/**
 * 新增留言指令（業務層輸入）。
 *
 * <p>與 {@code CreateCommentRequest} 分離的理由：業務層不得依賴展示層型別
 * （見 {@code business/package-info.java} 的依賴方向限制）。
 * 展示層負責把 HTTP 請求、路徑變數與已驗證身分組合為本型別。
 *
 * <p>不以三個裸參數傳遞的理由：{@code userId} 與 {@code postId} 同為 {@code Long}，
 * 位置互換編譯器不會報錯，留言會被記到錯誤的作者或發文上。具名欄位杜絕此類錯誤。
 *
 * @param userId  留言者。<b>取自已驗證身分，不由請求主體指定</b>——
 *                否則任何人皆可冒用他人身分留言（題目 §2）
 * @param postId  目標發文，取自 URI 路徑
 * @param content 留言內容，最長 500 字（ADR-005）
 */
public record CommentCreateCommand(
        Long userId,
        Long postId,
        String content) {
}
