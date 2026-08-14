package com.esunbank.social.business.service;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.esunbank.social.data.repository.CommentRepository;
import com.esunbank.social.data.repository.PostRepository;

/**
 * 發文業務邏輯（業務層）。
 *
 * <p>發文功能：新增、列出所有、編輯、刪除。
 *
 * <p><b>編輯與刪除不檢查發文者身分：</b>
 * 需求原文「確保只有登入的使用者可以<b>發文或留言</b>」，字面上未涵蓋編輯與刪除，
 * 需求方依「沒寫就不用」明示決定不實作，並接受「任何登入使用者可編輯或刪除他人發文」的風險。
 * <b>這是刻意決策，不是遺漏</b>——{@link #update} 與 {@link #delete} 因此不接受操作者身分。
 *
 * <p><b>未使用 {@code @Transactional}：</b>唯一的跨表寫入（刪除發文連動留言）之交易
 * 寫在 {@code sp_post_delete} 內。SP 自帶 {@code START TRANSACTION}，
 * 若本層再以 {@code @Transactional} 包一層，MySQL 會在進入 SP 時隱式提交外層交易，
 * 使交易邊界不清且回滾範圍與預期不符。新增與編輯則本就是單表操作，不需要交易。
 *
 * <p><b>為何由本服務注入 {@link CommentRepository}：</b>
 * 發文列表需帶出留言，而 {@code business/package-info.java} 明訂業務層「僅能呼叫 data 層」——
 * 改呼叫 {@code CommentService} 會形成業務層互相呼叫，牴觸該宣告。
 * 留言的讀取邏輯歸屬 亦符合 早已寫明的分工。
 */
@Service
public class PostService {

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public PostService(PostRepository postRepository, CommentRepository commentRepository) {
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
    }

    /**
     * 新增發文。
     *
     * @return 新增的 post_id
     */
    public Long create(PostCreateCommand command) {
        return postRepository.create(command.userId(), command.content());
    }

    /**
     * 列出所有未刪除的發文。
     *
     * <p>過濾條件 {@code is_deleted = FALSE} 在 {@code sp_post_list} 內，
     * 本層不再重複過濾——條件只該存在於一處，兩處各寫一份反而容易不同步。
     *
     * <p><b>一併帶出留言：</b>兩支 SP 各取一份資料，再依 {@code postId} 於記憶體分組——
     * 固定兩次資料庫往返，不因發文數增加而變多（逐篇查留言就是 N+1）。
     * 沒有留言的發文得到空 List 而非 null（見 {@link Post#comments()}）。
     *
     * <p>留言的順序由 {@code sp_comment_list_visible} 決定（依時間、以 comment_id 決勝），
     * 分組時原樣保留；發文本身的排序與分頁仍不實作。
     */
    public List<Post> listAll() {
        Map<Long, List<Comment>> commentsByPostId = commentsByPostId();

        return postRepository.findAll().stream()
                .map(row -> toDomain(row, commentsByPostId.getOrDefault(row.postId(), List.of())))
                .toList();
    }

    /**
     * 編輯發文。
     *
     * <p>先以 {@code sp_post_find_by_id} 確認目標存在且未被軟刪除，再更新——
     * 這是 明列的負面後果防範：不先檢查就會編輯到已刪除的內容。
     *
     * <p><b>不以 UPDATE 的影響列數判斷存在性：</b>MySQL 的 {@code ROW_COUNT()} 回報實際變更的
     * 列數，送出與原文相同的內容時為 0。若據此回 404，重送相同內容的編輯會被誤判為找不到。
     *
     * <p><b>回應必須帶上該篇原有的留言：</b>編輯只異動 content，留言不受影響。
     * 若回傳時漏掉 comments，前端以回應覆蓋卡片後，該篇的留言會在畫面上憑空消失。
     * {@code sp_post_find_by_id} 不回留言，故另行取一次。
     *
     * <p>此處取的是<b>全部</b>可見留言再挑出這一篇的——沒有「只取單篇留言」的 SP。
     * 可接受的理由：編輯是單筆操作，一次編輯就是一次多餘讀取，不會像列表那樣放大成 N+1。
     * 若日後留言量成長到讓這次讀取變得昂貴，再補一支依 post_id 取留言的 SP 即可，
     * 不必為了尚未發生的規模先增加一支 SP 與其測試。
     *
     * @return 更新後的發文。以先前查得的資料換上新內容組成，避免為了回應再查一次發文——
     *         {@code sp_post_update} 只異動 content（image 由資料層固定傳 null），其餘欄位不變
     * @throws PostNotFoundException 發文不存在或已被軟刪除
     */
    public Post update(PostUpdateCommand command) {
        PostRepository.PostRow existing = postRepository.findById(command.postId())
                .orElseThrow(() -> new PostNotFoundException(command.postId()));

        postRepository.update(command.postId(), command.content());

        return new Post(
                existing.postId(),
                existing.userId(),
                existing.userName(),
                command.content(),
                existing.createdAt(),
                commentsByPostId().getOrDefault(existing.postId(), List.of()));
    }

    /**
     * 刪除發文。
     *
     * <p>軟刪除：{@code sp_post_delete} 於同一交易內同時標記發文與其留言，
     * 為需求「需同時異動多個資料表時，請實作 Transaction」的唯一落地點。
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

    /**
     * 資料列 → 領域模型。
     *
     * <p>映射放在業務層而非資料層，是為了維持 {@code data/package-info.java} 宣告的
     * 依賴方向：資料層不得依賴業務層。資料層只回傳自己的
     * {@link PostRepository.PostRow}，由本層轉為領域模型。
     */
    private static Post toDomain(PostRepository.PostRow row, List<Comment> comments) {
        return new Post(
                row.postId(),
                row.userId(),
                row.userName(),
                row.content(),
                row.createdAt(),
                comments);
    }

    /**
     * 取回全部可見留言，依 {@code postId} 分組。
     *
     * <p>{@code groupingBy} 保留串流的相對順序，而 {@code sp_comment_list_visible} 已依
     * 時間（以 comment_id 決勝）排序，故同一則發文內的留言順序即為 SP 決定的順序——
     * 本層不再排序，排序規則只該存在於一處。
     */
    private Map<Long, List<Comment>> commentsByPostId() {
        return commentRepository.listVisible().stream()
                .collect(Collectors.groupingBy(
                        CommentRepository.CommentRow::postId,
                        Collectors.mapping(PostService::toDomain, Collectors.toList())));
    }

    /** 資料列 → 領域模型。{@code postId} 不映射——它是分組的鍵，見 {@link Comment}。 */
    private static Comment toDomain(CommentRepository.CommentRow row) {
        return new Comment(
                row.commentId(),
                row.userId(),
                row.userName(),
                row.content(),
                row.createdAt());
    }
}
