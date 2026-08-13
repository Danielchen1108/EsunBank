package com.esunbank.social.presentation.dto;

import java.time.LocalDateTime;

import com.esunbank.social.business.service.Post;

/**
 * 發文回應（列表與編輯共用）。
 *
 * <p><b>不含留言：</b>「列出所有發文」是否帶出留言題目未定義（{@code F004-REQ.md} OQ-1），
 * 決定為不帶出——維持 F004（發文）與 F005（留言）的職責分離，理由記於 {@code F004-API.md}。
 *
 * <p><b>不含 is_deleted：</b>回應中的發文必然是未刪除的（ADR-004 的讀取過濾在 SP 內），
 * 帶出這個旗標只會誘使前端自行判斷，反而製造漏過濾的機會。
 *
 * <p><b>XSS（題目 §6）：</b>{@code content} 為使用者輸入且會回顯，後端原樣儲存與回傳，
 * 跳脫由前端輸出端負責（Vue 的 {@code {{ }}} 插值預設跳脫 HTML）。
 * <b>前端不得對本欄位使用 {@code v-html}。</b>
 */
public record PostResponse(
        Long postId,
        Long userId,
        String userName,
        String content,
        String image,
        LocalDateTime createdAt) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.postId(),
                post.userId(),
                post.userName(),
                post.content(),
                post.image(),
                post.createdAt());
    }
}
