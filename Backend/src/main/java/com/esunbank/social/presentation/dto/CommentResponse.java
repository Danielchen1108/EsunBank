package com.esunbank.social.presentation.dto;

import java.time.LocalDateTime;

import com.esunbank.social.business.service.Comment;

/**
 * 留言回應（巢狀於 {@link PostResponse#comments()}）。
 *
 * <p>隨發文列表一併回傳（D-13）。沒有獨立的留言讀取端點——
 * 前端需要的是「每則發文底下的留言」，另開端點只會讓前端逐篇再查一次（N+1）。
 *
 * <p><b>不含 postId：</b>留言巢狀在所屬發文之下，歸屬關係由結構本身表達。
 * 在每則留言重複一次 postId，只是多出一個可能與外層不一致的欄位。
 *
 * <p><b>不含 is_deleted：</b>回應中的留言必然是可見的——
 * {@code sp_comment_list_visible} 同時過濾了留言與其所屬發文的軟刪除旗標（ADR-004）。
 *
 * <p><b>XSS（需求 §6）：</b>{@code content} 為使用者輸入且會回顯，後端原樣儲存與回傳，
 * 跳脫由前端輸出端負責（Vue 的 {@code {{ }}} 插值預設跳脫 HTML）。
 * <b>前端不得對本欄位使用 {@code v-html}</b>——留言是本案 XSS 防線的主要落點。
 */
public record CommentResponse(
        Long commentId,
        Long userId,
        String userName,
        String content,
        LocalDateTime createdAt) {

    public static CommentResponse from(Comment comment) {
        return new CommentResponse(
                comment.commentId(),
                comment.userId(),
                comment.userName(),
                comment.content(),
                comment.createdAt());
    }
}
