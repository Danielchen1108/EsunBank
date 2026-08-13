package com.esunbank.social.business.service;

import java.util.List;

import org.springframework.stereotype.Service;

import com.esunbank.social.data.repository.PostRepository;

/**
 * 發文業務邏輯（業務層）。
 *
 * <p>對應題目 §3 發文功能：新增、列出所有、編輯、刪除。
 *
 * <p><b>編輯與刪除不檢查發文者身分（{@code F004-REQ.md} § BG-4 裁決）：</b>
 * 題目 §2 原文「確保只有登入的使用者可以<b>發文或留言</b>」，字面上未涵蓋編輯與刪除，
 * owner 依「沒寫就不用」明示裁決不實作，並接受「任何登入使用者可編輯或刪除他人發文」的風險。
 * <b>這是刻意決策，不是遺漏</b>——{@link #update} 與 {@link #delete} 因此不接受操作者身分。
 *
 * <p><b>未使用 {@code @Transactional}：</b>唯一的跨表寫入（刪除發文連動留言）之交易
 * 寫在 {@code sp_post_delete} 內（ADR-004）。SP 自帶 {@code START TRANSACTION}，
 * 若本層再以 {@code @Transactional} 包一層，MySQL 會在進入 SP 時隱式提交外層交易，
 * 使交易邊界不清且回滾範圍與預期不符。新增與編輯則本就是單表操作，不需要交易。
 */
@Service
public class PostService {

    private final PostRepository postRepository;

    public PostService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    /**
     * 新增發文（AC-1）。
     *
     * @return 新增的 post_id
     */
    public Long create(PostCreateCommand command) {
        return postRepository.create(command.userId(), command.content(), command.image());
    }

    /**
     * 列出所有未刪除的發文（AC-3）。
     *
     * <p>過濾條件 {@code is_deleted = FALSE} 在 {@code sp_post_list} 內（ADR-004），
     * 本層不再重複過濾——條件只該存在於一處，兩處各寫一份反而容易不同步。
     *
     * <p>不帶出留言（OQ-1 決定），排序與分頁亦不實作（`SCOPE-BOUNDARY.md` Out of Scope）。
     */
    public List<Post> listAll() {
        return postRepository.findAll();
    }

    /**
     * 編輯發文（AC-4）。
     *
     * <p>先以 {@code sp_post_find_by_id} 確認目標存在且未被軟刪除，再更新——
     * 這是 ADR-004 明列的負面後果防範：不先檢查就會編輯到已刪除的內容。
     *
     * <p><b>不以 UPDATE 的影響列數判斷存在性：</b>MySQL 的 {@code ROW_COUNT()} 回報實際變更的
     * 列數，送出與原文相同的內容時為 0。若據此回 404，重送相同內容的編輯會被誤判為找不到。
     *
     * @return 更新後的發文。以先前查得的資料換上新內容組成，避免為了回應再查一次資料庫——
     *         {@code sp_post_update} 只異動 content 與 image，其餘欄位不變
     * @throws PostNotFoundException 發文不存在或已被軟刪除
     */
    public Post update(PostUpdateCommand command) {
        Post existing = postRepository.findById(command.postId())
                .orElseThrow(() -> new PostNotFoundException(command.postId()));

        postRepository.update(command.postId(), command.content(), command.image());

        return new Post(
                existing.postId(),
                existing.userId(),
                existing.userName(),
                command.content(),
                command.image(),
                existing.createdAt());
    }

    /**
     * 刪除發文（AC-5、AC-11）。
     *
     * <p>軟刪除：{@code sp_post_delete} 於同一交易內同時標記發文與其留言（ADR-004），
     * 為題目 §6「需同時異動多個資料表時，請實作 Transaction」的唯一落地點。
     *
     * <p>此處可用影響列數判斷存在性：{@code is_deleted} 由 FALSE 改為 TRUE 必然造成變更，
     * 回 0 即代表發文不存在或先前已被刪除——與編輯的情況不同。
     *
     * @throws PostNotFoundException 發文不存在或已被軟刪除
     */
    public void delete(Long postId) {
        if (postRepository.softDelete(postId) == 0) {
            throw new PostNotFoundException(postId);
        }
    }
}
