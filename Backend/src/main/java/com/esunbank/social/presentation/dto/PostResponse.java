package com.esunbank.social.presentation.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.esunbank.social.business.service.Post;

/**
 * 發文回應（列表與編輯共用）。
 *
 * <p><b>含留言：</b>「列出所有發文」是否帶出留言題目未定義（{@code F004-REQ.md} OQ-1），
 * 原決定為不帶出，後由 owner 明示追加（D-13）。留言巢狀於 {@link #comments()}，
 * 讓前端一次請求就取得整個畫面所需的資料，不必逐篇再查（N+1）。
 *
 * <p>沒有留言的發文回傳空陣列而非 null——{@code []} 與 {@code null} 在前端得分開處理，
 * 但語意上並無差別，統一為空陣列可省掉那個判斷。
 *
 * <p><b>不含 is_deleted：</b>回應中的發文必然是未刪除的（ADR-004 的讀取過濾在 SP 內），
 * 帶出這個旗標只會誘使前端自行判斷，反而製造漏過濾的機會。
 *
 * <p><b>不含 image：</b>{@code post.image} 保留於 schema（題目第 2 頁），但無上傳功能，
 * API 亦不開放填寫（見 {@link CreatePostRequest}）。回傳一個永遠為 null 的欄位
 * 只會讓前端誤以為有圖片可顯示（`SCOPE-BOUNDARY.md` R-3）。
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
        LocalDateTime createdAt,
        List<CommentResponse> comments) {

    public static PostResponse from(Post post) {
        return new PostResponse(
                post.postId(),
                post.userId(),
                post.userName(),
                post.content(),
                post.createdAt(),
                post.comments().stream()
                        .map(CommentResponse::from)
                        .toList());
    }
}
