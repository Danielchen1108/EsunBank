package com.esunbank.social.data.repository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import com.esunbank.social.common.exception.CommentTargetPostNotFoundException;

/**
 * 新增留言的端到端資料層驗證（F005 AC-3、AC-6、AC-7）。
 *
 * <p>單元測試以 mock 取代資料層，證明不了兩件只有真實 MySQL 才成立的事：
 * <ul>
 *   <li>{@code sp_comment_create} 的 {@code SIGNAL SQLSTATE '45000'}
 *       確實被轉譯為 {@link CommentTargetPostNotFoundException}</li>
 *   <li>注入字串經 {@code CallableStatement} 綁定後原樣存為文字（題目 §6）</li>
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
     * 僅測試用。驗證寫入結果需要讀取，但讀取留言不在 F005 範圍內
     * （{@code SCOPE-BOUNDARY.md} 明列「列出留言」為 Out of Scope），
     * 故不為此新增 Stored Procedure 或資料層方法。
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
}
