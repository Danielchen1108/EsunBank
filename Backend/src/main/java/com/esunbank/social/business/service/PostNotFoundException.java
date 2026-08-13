package com.esunbank.social.business.service;

/**
 * 找不到目標發文（業務層領域例外）。
 *
 * <p>兩種情境都拋這個例外：發文不存在，或已被軟刪除（ADR-004）。
 * 對呼叫端而言兩者等價——已刪除的發文不該再被編輯或刪除，
 * 且回應也不應洩漏「這筆曾經存在」。
 *
 * <p>置於業務層而非共用層的 {@code common.exception}：F003 與 F005 正同時開發，
 * 共用層檔案由其他功能負責，此例外僅供發文功能使用，放在自己的層別可避免修改衝突。
 */
public class PostNotFoundException extends RuntimeException {

    private final Long postId;

    public PostNotFoundException(Long postId) {
        super("找不到發文：" + postId);
        this.postId = postId;
    }

    public Long getPostId() {
        return postId;
    }
}
