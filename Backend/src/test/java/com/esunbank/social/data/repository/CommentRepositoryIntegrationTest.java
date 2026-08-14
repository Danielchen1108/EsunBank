package com.esunbank.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import com.esunbank.social.common.exception.CommentTargetPostNotFoundException;

/**
 * 留言資料層的端到端驗證（F005 AC-3、AC-6、AC-7；讀取部分為 F004 D-13）。
 *
 * <p>單元測試以 mock 取代資料層，證明不了幾件只有真實 MySQL 才成立的事：
 * <ul>
 *   <li>{@code sp_comment_create} 的 {@code SIGNAL SQLSTATE '45000'}
 *       確實被轉譯為 {@link CommentTargetPostNotFoundException}</li>
 *   <li>注入字串經 {@code CallableStatement} 綁定後原樣存為文字（題目 §6）</li>
 *   <li>{@code sp_comment_list_visible} 的兩層 {@code is_deleted} 過濾與排序
 *       ——過濾與排序都寫在 SP 內，mock 掉資料層就等於把要驗的東西一起 mock 掉了</li>
 * </ul>
 *
 * <p><b>為何預設不執行：</b>本測試需要一個已灌入 {@code DB/} 三支腳本的 MySQL 實例。
 * 若無條件執行，其他人在沒有資料庫時跑 {@code ./mvnw test} 會失敗。
 * 啟用方式（見 {@code F005-TR.md} 端到端驗證）：
 *
 * <pre>{@code
 * ./mvnw test -Df005.integration=true -Dtest=CommentRepositoryIntegrationTest
 * }</pre>
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3310/esunbank_social"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Taipei",
        "spring.datasource.username=root",
        "spring.datasource.password="
})
@EnabledIfSystemProperty(named = "f005.integration", matches = "true")
class CommentRepositoryIntegrationTest {

    @Autowired
    private CommentRepository commentRepository;

    /**
     * 僅測試用。用於直接檢查資料列，以及佈置 SP 或資料層刻意不提供的邊界狀態
     * （例如軟刪除單一留言、製造 TD-002 的孤兒留言）——
     * 那些不是產品功能，不該為了測試而在 {@code DB/} 或資料層增加對應方法。
     */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final long EXISTING_USER_ID = 1L;
    private static final long EXISTING_POST_ID = 1L;

    private Long softDeletedPostId() {
        return jdbcTemplate.queryForObject(
                "SELECT post_id FROM `post` WHERE is_deleted = TRUE ORDER BY post_id LIMIT 1",
                Long.class);
    }

    @Test
    @DisplayName("對存在且未刪除的發文新增留言成功，內容原樣寫入")
    void createsCommentOnExistingPost() {
        String content = "整合測試留言 🎯";

        Long commentId = commentRepository.create(EXISTING_USER_ID, EXISTING_POST_ID, content);

        assertThat(commentId).isNotNull().isPositive();

        var row = jdbcTemplate.queryForMap(
                "SELECT user_id, post_id, content, is_deleted, created_at "
                        + "FROM `comment` WHERE comment_id = ?", commentId);

        assertThat(row.get("content")).isEqualTo(content);
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(EXISTING_USER_ID);
        assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(EXISTING_POST_ID);
        // created_at 由資料庫預設值產生（F001 AC-14），應用層不填
        assertThat(row.get("created_at")).isNotNull();
        assertThat(row.get("is_deleted")).isEqualTo(false);
    }

    @Test
    @DisplayName("對已軟刪除的發文新增留言被 SP 擋下（ADR-004）")
    void rejectsSoftDeletedPost() {
        Long deletedPostId = softDeletedPostId();
        assertThat(deletedPostId).as("種子資料應含一筆已軟刪除的發文").isNotNull();

        assertThatThrownBy(() -> commentRepository.create(EXISTING_USER_ID, deletedPostId, "不該寫入"))
                .isInstanceOf(CommentTargetPostNotFoundException.class);

        Long written = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `comment` WHERE post_id = ? AND content = ?",
                Long.class, deletedPostId, "不該寫入");
        assertThat(written).isZero();
    }

    @Test
    @DisplayName("對不存在的 post_id 新增留言被擋下")
    void rejectsMissingPost() {
        assertThatThrownBy(() -> commentRepository.create(EXISTING_USER_ID, 999_999L, "不該寫入"))
                .isInstanceOf(CommentTargetPostNotFoundException.class);
    }

    @Test
    @DisplayName("SQL Injection 字串原樣存為文字，comment 表完好（題目 §6）")
    void storesInjectionStringAsPlainText() {
        String injection = "'); DROP TABLE `comment`; --";
        Long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `comment`", Long.class);

        Long commentId = commentRepository.create(EXISTING_USER_ID, EXISTING_POST_ID, injection);

        String stored = jdbcTemplate.queryForObject(
                "SELECT content FROM `comment` WHERE comment_id = ?", String.class, commentId);
        assertThat(stored).isEqualTo(injection);

        Long after = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `comment`", Long.class);
        assertThat(after).isEqualTo(before + 1);
    }

    // -------------------------------------------------------------------------
    // listVisible()（F004 D-13）
    //
    // 以下測試多半要先佈置「不該被看見」的資料。改狀態的測試一律加 @Transactional，
    // 讓 Spring 測試框架在結束時回滾——種子資料是共用的，測完留下髒資料，
    // 下一次執行就會踩到別的測試留下的痕跡。
    // -------------------------------------------------------------------------

    @Test
    @DisplayName("列出可見留言：帶出留言者名稱與所屬 post_id，供業務層分組")
    void listsVisibleCommentsWithAuthorAndOwningPost() {
        Long expected = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM `comment` c JOIN `post` p ON p.post_id = c.post_id "
                        + "WHERE c.is_deleted = FALSE AND p.is_deleted = FALSE",
                Long.class);

        var rows = commentRepository.listVisible();

        assertThat(rows).hasSize(expected.intValue());
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.commentId()).isNotNull().isPositive();
            assertThat(row.postId()).isNotNull().isPositive();
            assertThat(row.userId()).isNotNull().isPositive();
            // user_name 由 SP 內 JOIN user 帶出——沒有它，前端只能顯示一組 userId
            assertThat(row.userName()).isNotBlank();
            assertThat(row.content()).isNotNull();
            assertThat(row.createdAt()).isNotNull();
        });
    }

    @Test
    @DisplayName("列出可見留言：排除已軟刪除的留言（ADR-004）")
    @Transactional
    void excludesSoftDeletedComments() {
        Long commentId = commentRepository.create(EXISTING_USER_ID, EXISTING_POST_ID, "這則將被軟刪除");
        assertThat(commentRepository.listVisible())
                .extracting(CommentRepository.CommentRow::commentId)
                .contains(commentId);

        jdbcTemplate.update("UPDATE `comment` SET is_deleted = TRUE WHERE comment_id = ?", commentId);

        assertThat(commentRepository.listVisible())
                .extracting(CommentRepository.CommentRow::commentId)
                .doesNotContain(commentId);
    }

    @Test
    @DisplayName("列出可見留言：排除掛在已刪除發文下的孤兒留言（TECH_DEBT TD-002）")
    @Transactional
    void excludesOrphanCommentsOnSoftDeletedPost() {
        Long deletedPostId = softDeletedPostId();
        assertThat(deletedPostId).as("種子資料應含一筆已軟刪除的發文").isNotNull();

        // 直接 INSERT 而非走 sp_comment_create：後者會擋下對已刪除發文的留言。
        // 這裡要重現的正是 TD-002 的競態產物——存在性檢查通過後、寫入前發文才被刪除，
        // 於是留下一則 is_deleted = FALSE 卻掛在已刪除發文下的孤兒留言。
        // 過去沒有讀取管道所以看不見；一旦列表帶出留言，它就會浮上畫面。
        jdbcTemplate.update(
                "INSERT INTO `comment` (user_id, post_id, content, is_deleted) VALUES (?, ?, ?, FALSE)",
                EXISTING_USER_ID, deletedPostId, "孤兒留言，不該被看見");

        assertThat(commentRepository.listVisible())
                .as("SP 同時過濾 comment 與 post 的 is_deleted，正確性不依賴呼叫端")
                .extracting(CommentRepository.CommentRow::postId)
                .doesNotContain(deletedPostId);
    }

    @Test
    @DisplayName("列出可見留言：同一發文內依時間由舊到新，同秒者以 comment_id 決勝")
    @Transactional
    void ordersCommentsByTimeWithinEachPost() {
        // 刻意以「插入順序與時間順序相反」的方式寫入：若 SP 漏了 ORDER BY，
        // 結果會恰好呈現插入順序，測試就會紅。
        insertCommentAt(EXISTING_POST_ID, "第三則", "2026-08-13 12:00:30");
        insertCommentAt(EXISTING_POST_ID, "第一則", "2026-08-13 12:00:10");
        insertCommentAt(EXISTING_POST_ID, "第二則", "2026-08-13 12:00:20");
        // created_at 為 DATETIME（秒精度），同秒的兩則只靠時間排序順序不確定，
        // 故 SP 以 comment_id 當決勝欄位——先寫入的 id 較小，即較早。
        Long earlierSameSecond = insertCommentAt(EXISTING_POST_ID, "同秒較早", "2026-08-13 12:00:40");
        Long laterSameSecond = insertCommentAt(EXISTING_POST_ID, "同秒較晚", "2026-08-13 12:00:40");

        var contents = commentRepository.listVisible().stream()
                .filter(row -> row.postId().equals(EXISTING_POST_ID))
                .map(CommentRepository.CommentRow::content)
                .toList();

        assertThat(contents).containsSubsequence("第一則", "第二則", "第三則", "同秒較早", "同秒較晚");
        assertThat(earlierSameSecond).isLessThan(laterSameSecond);
    }

    @Test
    @DisplayName("列出可見留言：全部不可見時回空 List 而非 null")
    @Transactional
    void returnsEmptyListWhenNothingVisible() {
        // 在交易內把所有留言標記為已刪除，測試結束回滾——
        // 種子資料永遠有可見留言，不佈置就驗不到「查無資料」這條路徑。
        jdbcTemplate.update("UPDATE `comment` SET is_deleted = TRUE");

        assertThat(commentRepository.listVisible()).isNotNull().isEmpty();
    }

    /** 以指定 created_at 寫入留言（SP 不開放指定時間，僅供排序測試佈置）。 */
    private Long insertCommentAt(long postId, String content, String createdAt) {
        jdbcTemplate.update(
                "INSERT INTO `comment` (user_id, post_id, content, created_at) VALUES (?, ?, ?, ?)",
                EXISTING_USER_ID, postId, content, createdAt);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
