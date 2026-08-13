package com.esunbank.social.business.service;

import java.time.LocalDateTime;

/**
 * 發文領域模型（業務層）。
 *
 * <p>對應題目第 2 頁 Post 表，欄位由 {@code sp_post_list} / {@code sp_post_find_by_id} 取得。
 *
 * <p><b>不含 {@code is_deleted}：</b>軟刪除是持久化層的機制（ADR-004），
 * 讀取用的 SP 已保證只回傳未刪除的資料。若讓這個旗標流入業務層，
 * 每個使用端都得再判斷一次「這筆是不是已刪除」——那正是軟刪除最容易漏掉過濾的地方。
 *
 * <p><b>不含留言：</b>「列出所有發文」是否帶出留言，題目未定義（F004-REQ.md OQ-1）。
 * 決定為不帶出，維持 F004（發文）與 F005（留言）的職責分離；理由記於 {@code F004-API.md}。
 *
 * <p><b>不含 image：</b>{@code post.image} 欄位保留於 schema（題目第 2 頁列有 Image），
 * 但題目功能清單無上傳功能，API 不開放填寫（`SCOPE-BOUNDARY.md` R-3）。
 * 讀取的 SP 仍會選出該欄，本層刻意不映射——沒有寫入來源的欄位一路帶到前端，
 * 只是一個永遠為 null 的擺設。
 *
 * @param postId    發文 ID，對應 {@code post.post_id}
 * @param userId    發文者 ID，對應 {@code post.user_id}
 * @param userName  發文者名稱，由 SP 內 JOIN {@code user} 帶出，供列表顯示
 * @param content   發文內容，最長 2000 字元（ADR-005）
 * @param createdAt 發佈時間，由資料庫預設值產生（F001 AC-14）
 */
public record Post(
        Long postId,
        Long userId,
        String userName,
        String content,
        LocalDateTime createdAt) {
}
