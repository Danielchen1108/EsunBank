package com.esunbank.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.esunbank.social.data.repository.PostRepository.PostRow;

/**
 * 發文資料層對真實 MySQL 的整合測試。
 *
 * <p><b>為何需要真的連資料庫：</b>本測試要驗證的行為全部發生在 Stored Procedure 內部——
 * 軟刪除的讀取過濾、跨表連動標記、交易回滾。以 mock 取代資料層只會驗證到 mock 自己的設定，
 * 證明不了 SP 的行為。
 *
 * <p><b>預設略過：</b>需以環境變數 {@code F004_IT_DB=true} 啟用，避免沒有資料庫的環境
 * （其他功能的開發者、CI）跑 {@code ./mvnw test} 時失敗。啟用方式：
 *
 * <pre>{@code
 * mysql -h 127.0.0.1 -P 3309 -u root < DB/01_DDL.sql
 * mysql -h 127.0.0.1 -P 3309 -u root < DB/02_DDL_stored_procedures.sql
 * mysql -h 127.0.0.1 -P 3309 -u root < DB/03_DML.sql
 * F004_IT_DB=true ./mvnw test -Dtest=PostRepositoryIntegrationTest
 * }</pre>
 *
 * <p><b>驗證用的直接 SQL：</b>斷言 {@code is_deleted} 狀態時刻意繞過 Stored Procedure 直接查詢。
 * 若改用受測的 SP 來驗證 SP 自己的效果，測試與實作會一起錯而測不出來。
 * 此處的 SQL 僅存在於測試，不違反「資料層不得寫 SQL」的約束。
 */
@SpringBootTest(
        properties = {
                "spring.datasource.url=jdbc:mysql://127.0.0.1:3309/esunbank_social"
                        + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Taipei",
                "spring.datasource.username=root",
                "spring.datasource.password="
        })
@EnabledIfEnvironmentVariable(named = "F004_IT_DB", matches = "true")
class PostRepositoryIntegrationTest {

    private static final long SEED_USER_ID = 1L;

    @Autowired
    private PostRepository postRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Long insertPostWithComment(String content) {
        Long postId = postRepository.create(SEED_USER_ID, content);
        jdbcTemplate.update(
                "INSERT INTO `comment` (user_id, post_id, content) VALUES (?, ?, ?)",
                SEED_USER_ID, postId, "整合測試留言");
        return postId;
    }

    /**
     * 以指定 created_at 寫入發文，僅供排序測試佈置。
     *
     * <p>刻意繞過 {@code sp_post_create}：該 SP 不開放指定時間，發佈時間一律由 DB 層產生。
     * 要驗證排序就必須造出可控的時間差，這是唯一的辦法。
     */
    private Long insertPostAt(String content, String createdAt) {
        jdbcTemplate.update(
                "INSERT INTO `post` (user_id, content, created_at) VALUES (?, ?, ?)",
                SEED_USER_ID, content, createdAt);

        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private int postDeletedFlag(Long postId) {
        return jdbcTemplate.queryForObject(
                "SELECT is_deleted FROM `post` WHERE post_id = ?", Integer.class, postId);
    }

    private List<Integer> commentDeletedFlags(Long postId) {
        return jdbcTemplate.queryForList(
                "SELECT is_deleted FROM `comment` WHERE post_id = ?", Integer.class, postId);
    }

    @Test
    @DisplayName("新增發文：sp_post_create 回傳新 post_id，內容與作者正確")
    void createsPost() {
        Long postId = postRepository.create(SEED_USER_ID, "整合測試發文 🎯");

        assertThat(postId).isNotNull();

        Optional<PostRow> found = postRepository.findById(postId);
        assertThat(found).isPresent();
        assertThat(found.get().content()).isEqualTo("整合測試發文 🎯");
        assertThat(found.get().userId()).isEqualTo(SEED_USER_ID);
        assertThat(found.get().userName()).isEqualTo("陳大文");
        assertThat(found.get().createdAt()).isNotNull();
    }

    @Test
    @DisplayName("列出所有發文：sp_post_list 帶 is_deleted = FALSE，已刪除的不出現")
    void listExcludesSoftDeletedPosts() {
        Long visibleId = postRepository.create(SEED_USER_ID, "這篇看得見");
        Long deletedId = postRepository.create(SEED_USER_ID, "這篇會被刪除");
        postRepository.softDelete(deletedId);

        List<PostRow> posts = postRepository.findAll();

        assertThat(posts).extracting(PostRow::postId).contains(visibleId);
        assertThat(posts).extracting(PostRow::postId).doesNotContain(deletedId);
    }

    @Test
    @DisplayName("列出所有發文：由新到舊排序——排序寫在 sp_post_list，不在應用層")
    void listOrdersPostsNewestFirst() {
        // 刻意讓插入順序與時間順序相反：SP 若漏了 ORDER BY，
        // 回傳會是插入順序，斷言就會失敗。
        Long oldest = insertPostAt("最舊", "2020-01-01 00:00:00");
        Long newest = insertPostAt("最新", "2030-01-01 00:00:00");
        Long middle = insertPostAt("中間", "2025-01-01 00:00:00");

        List<Long> ids = postRepository.findAll().stream().map(PostRow::postId).toList();

        // 只比較這三筆的相對順序，不假設種子資料的內容
        assertThat(ids).containsSubsequence(newest, middle, oldest);
    }

    @Test
    @DisplayName("列出所有發文：created_at 同秒時以 post_id DESC 決勝——DATETIME 只有秒精度")
    void listBreaksTiesByPostIdDescending() {
        String sameSecond = "2029-06-15 12:00:00";
        Long first = insertPostAt("同秒 A", sameSecond);
        Long second = insertPostAt("同秒 B", sameSecond);

        List<Long> ids = postRepository.findAll().stream().map(PostRow::postId).toList();

        // 後寫入的 post_id 較大，DESC 下應排在前面
        assertThat(second).isGreaterThan(first);
        assertThat(ids).containsSubsequence(second, first);
    }

    @Test
    @DisplayName("取單筆發文：已軟刪除者查不到")
    void findByIdExcludesSoftDeletedPost() {
        Long postId = postRepository.create(SEED_USER_ID, "即將被刪除");
        postRepository.softDelete(postId);

        assertThat(postRepository.findById(postId)).isEmpty();
    }

    @Test
    @DisplayName("編輯發文：內容確實更新")
    void updatesPostContent() {
        Long postId = postRepository.create(SEED_USER_ID, "原始內容");

        int affected = postRepository.update(postId, "修改後內容");

        assertThat(affected).isEqualTo(1);
        assertThat(postRepository.findById(postId))
                .get()
                .satisfies(p -> assertThat(p.content()).isEqualTo("修改後內容"));
        // image 不由 API 提供（欄位保留於 schema 但無上傳功能，R-3），
        // 資料層固定綁 NULL；sp_post_update 為整體取代語意，故編輯後該欄為 NULL。
        assertThat(jdbcTemplate.queryForObject(
                "SELECT image FROM `post` WHERE post_id = ?", String.class, postId)).isNull();
    }

    @Test
    @DisplayName("編輯已軟刪除的發文：不生效——sp_post_update 帶 is_deleted = FALSE")
    void updateDoesNotTouchSoftDeletedPost() {
        Long postId = postRepository.create(SEED_USER_ID, "刪除前的內容");
        postRepository.softDelete(postId);

        int affected = postRepository.update(postId, "不該寫進去");

        assertThat(affected).isZero();
        String content = jdbcTemplate.queryForObject(
                "SELECT content FROM `post` WHERE post_id = ?", String.class, postId);
        assertThat(content).isEqualTo("刪除前的內容");
    }

    @Test
    @DisplayName("刪除發文：同一交易內連動軟刪除其留言，且發文不再出現於列表")
    void deleteSoftDeletesPostAndItsCommentsInOneTransaction() {
        Long postId = insertPostWithComment("這篇連同留言一起被軟刪除");

        assertThat(postDeletedFlag(postId)).isZero();
        assertThat(commentDeletedFlags(postId)).containsExactly(0);

        int affected = postRepository.softDelete(postId);

        assertThat(affected).isEqualTo(1);
        // 需求：跨表異動須為原子操作——post 與 comment 必須同時標記
        assertThat(postDeletedFlag(postId)).isEqualTo(1);
        assertThat(commentDeletedFlags(postId)).containsExactly(1);
        // 最常見的缺陷：讀取漏帶過濾條件，已刪除的內容重新出現
        assertThat(postRepository.findAll()).extracting(PostRow::postId).doesNotContain(postId);
        assertThat(postRepository.findById(postId)).isEmpty();
    }

    @Test
    @DisplayName("重複刪除同一發文：第二次回報 0 列，供應用層判定 404")
    void deletingTwiceAffectsNoRowsTheSecondTime() {
        Long postId = postRepository.create(SEED_USER_ID, "刪兩次");

        assertThat(postRepository.softDelete(postId)).isEqualTo(1);
        assertThat(postRepository.softDelete(postId)).isZero();
    }

    @Test
    @DisplayName("刪除發文失敗時整筆回滾——post 不會停留在「已刪除但留言未刪」的錯亂狀態")
    void deleteRollsBackWhenCommentUpdateFails() {
        Long postId = insertPostWithComment("驗證交易回滾");

        // 以 trigger 讓 comment 的 UPDATE 必定失敗。sp_post_delete 先更新 post 再更新 comment，
        // 若沒有交易，post 會停在 is_deleted = 1 而留言未動——正是需求所述的資料錯亂。
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_f004_fail_comment_update
                BEFORE UPDATE ON `comment`
                FOR EACH ROW
                SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'rollback test'
                """);
        try {
            assertThatThrownBy(() -> postRepository.softDelete(postId))
                    .isInstanceOf(org.springframework.dao.DataAccessException.class);

            assertThat(postDeletedFlag(postId)).as("post 應被回滾為未刪除").isZero();
            assertThat(commentDeletedFlags(postId)).containsExactly(0);
        } finally {
            jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_f004_fail_comment_update");
        }

        // 移除 trigger 後重試應成功，確認上一步失敗的是 trigger 而非資料本身
        assertThat(postRepository.softDelete(postId)).isEqualTo(1);
    }

    @Test
    @DisplayName("SQL Injection 字串以純文字寫入，資料表完好")
    void treatsInjectionPayloadAsPlainText() {
        String payload = "'); DROP TABLE `post`; --";

        Long postId = postRepository.create(SEED_USER_ID, payload);

        assertThat(postRepository.findById(postId)).get()
                .satisfies(p -> assertThat(p.content()).isEqualTo(payload));
        // 資料表仍在：能查到列數即代表未被 DROP
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `post`", Integer.class))
                .isPositive();
    }
}
