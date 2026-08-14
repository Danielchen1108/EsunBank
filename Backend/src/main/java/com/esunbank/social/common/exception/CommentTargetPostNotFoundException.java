package com.esunbank.social.common.exception;

/**
 * 留言的目標發文不存在，或已被軟刪除。
 *
 * <p>由資料層自 {@code sp_comment_create} 的
 * {@code SIGNAL SQLSTATE '45000'} 轉譯而來——目標發文是否可留言，
 * 最終判定者是 SP。
 *
 * <p><b>為何「不存在」與「已刪除」共用同一個例外：</b>
 * 軟刪除是內部實作，對用戶端而言已刪除的發文就是不存在。
 * 若分成兩種錯誤，等於告訴呼叫者「這篇發文曾經存在」——沒有必要外洩。
 *
 * <p>需求未定義此情境，但 SP 的存在性檢查
 * 使其必然發生，故須明確處理而非讓資料庫例外直接外洩。
 */
public class CommentTargetPostNotFoundException extends RuntimeException {

    private final Long postId;

    public CommentTargetPostNotFoundException(Long postId) {
        super("發文不存在或已被刪除：" + postId);
        this.postId = postId;
    }

    public Long getPostId() {
        return postId;
    }
}
