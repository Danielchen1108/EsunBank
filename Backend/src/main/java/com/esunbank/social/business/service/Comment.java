package com.esunbank.social.business.service;

import java.time.LocalDateTime;

/**
 * 留言領域模型（業務層）。
 *
 * <p>對應需求規格 Comment 表，欄位由 {@code sp_comment_list_visible} 取得，
 * 巢狀於 {@link Post#comments()} 中隨發文列表一併回傳（D-13）。
 *
 * <p><b>不含 {@code postId}：</b>它是業務層分組時用的鍵，不是留言本身的屬性。
 * 留言已經掛在所屬的 {@link Post} 底下，再帶一次歸屬資訊只會多出一個可能與外層不一致的欄位。
 *
 * <p><b>不含 {@code is_deleted}：</b>理由同 {@link Post}——軟刪除是持久化層的機制（ADR-004），
 * {@code sp_comment_list_visible} 已保證只回傳未刪除的留言（且其所屬發文也未刪除）。
 *
 * @param commentId 留言 ID，對應 {@code comment.comment_id}
 * @param userId    留言者 ID，對應 {@code comment.user_id}
 * @param userName  留言者名稱，由 SP 內 JOIN {@code user} 帶出，供列表顯示
 * @param content   留言內容
 * @param createdAt 留言時間，由資料庫預設值產生（F001 AC-14）
 */
public record Comment(
        Long commentId,
        Long userId,
        String userName,
        String content,
        LocalDateTime createdAt) {
}
