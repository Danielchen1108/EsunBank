package com.esunbank.social.business.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 發文領域模型（業務層）。
 *
 * <p>對應需求規格 Post 表，欄位由 {@code sp_post_list} / {@code sp_post_find_by_id} 取得。
 *
 * <p><b>不含 {@code is_deleted}：</b>軟刪除是持久化層的機制（ADR-004），
 * 讀取用的 SP 已保證只回傳未刪除的資料。若讓這個旗標流入業務層，
 * 每個使用端都得再判斷一次「這筆是不是已刪除」——那正是軟刪除最容易漏掉過濾的地方。
 *
 * <p><b>含留言：</b>「列出所有發文」是否帶出留言需求未定義（F004-REQ.md OQ-1），
 * 原判定為不帶出；後由 owner 明示追加（D-13）。留言若無讀取管道，使用者留完言就再也看不到，
 * 對話便不成立。改為隨發文一併帶出，順帶讓前端不必逐篇再查一次（N+1）。
 * 讀取邏輯歸 F004，F005 仍只負責新增（{@code F005-REQ.md}）。
 *
 * <p><b>不含 image：</b>{@code post.image} 欄位保留於 schema（需求規格列有 Image），
 * 但需求功能清單無上傳功能，API 不開放填寫（`SCOPE-BOUNDARY.md` R-3）。
 * 讀取的 SP 仍會選出該欄，本層刻意不映射——沒有寫入來源的欄位一路帶到前端，
 * 只是一個永遠為 null 的擺設。
 *
 * @param postId    發文 ID，對應 {@code post.post_id}
 * @param userId    發文者 ID，對應 {@code post.user_id}
 * @param userName  發文者名稱，由 SP 內 JOIN {@code user} 帶出，供列表顯示
 * @param content   發文內容，最長 2000 字元（ADR-005）
 * @param createdAt 發佈時間，由資料庫預設值產生（F001 AC-14）
 * @param comments  本則發文的留言，依時間由舊到新排序；沒有留言時為空 List，<b>不為 null</b>
 *                  ——空集合與「未載入」在此不需要區分，用 null 表達只會逼每個使用端多判一次
 */
public record Post(
        Long postId,
        Long userId,
        String userName,
        String content,
        LocalDateTime createdAt,
        List<Comment> comments) {
}
