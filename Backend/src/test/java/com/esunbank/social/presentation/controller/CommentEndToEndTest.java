package com.esunbank.social.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.esunbank.social.common.security.AuthenticatedUser;

/**
 * 新增留言的全鏈路驗證：展示層 → 業務層 → 資料層 → 真實 MySQL。
 *
 * <p>與 {@code CommentControllerTest} 的分工：該測試以 mock 取代業務層，驗證控制器本身；
 * 本測試不 mock 任何一層，驗證四層確實接得起來，且 SP 的錯誤真的會變成 404。
 *
 * <p><b>未涵蓋：安全過濾鏈。</b>{@code addFilters = false} 的理由同
 * {@code CommentControllerTest}——本測試要驗的是四層接得起來，不重複驗證過濾鏈，
 * 也就不需要產生型別為 {@link AuthenticatedUser} 的 principal。
 * 「未登入者會被擋下」由 {@code SecurityConfigTest} 以實際 HTTP 請求驗證。
 *
 * <p>啟用方式與 {@code CommentRepositoryIntegrationTest} 相同，需 3310 埠的 MySQL 實例。
 */
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:mysql://127.0.0.1:3310/esunbank_social"
                + "?useUnicode=true&characterEncoding=UTF-8&serverTimezone=Asia/Taipei",
        "spring.datasource.username=root",
        "spring.datasource.password="
})
@AutoConfigureMockMvc(addFilters = false)
@EnabledIfSystemProperty(named = "f005.integration", matches = "true")
class CommentEndToEndTest {

    @Autowired
    private MockMvc mockMvc;

    /** 僅測試用，用於確認寫入結果。 */
    @Autowired
    private JdbcTemplate jdbcTemplate;

    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(2L, "0922333444");

    @BeforeEach
    void authenticateCurrentUser() {
        TestSecurityContextHolder.setAuthentication(
                new UsernamePasswordAuthenticationToken(CURRENT_USER, null, List.of()));
    }

    @AfterEach
    void clearCurrentUser() {
        TestSecurityContextHolder.clearContext();
    }

    private static String bodyWith(String content) {
        return "{\"content\": \"" + content.replace("\\", "\\\\").replace("\"", "\\\"") + "\"}";
    }

    private Long softDeletedPostId() {
        return jdbcTemplate.queryForObject(
                "SELECT post_id FROM `post` WHERE is_deleted = TRUE ORDER BY post_id LIMIT 1",
                Long.class);
    }

    @Test
    @DisplayName("對存在的發文新增留言：201，且資料庫確實出現該列")
    void createsCommentThroughAllLayers() throws Exception {
        String content = "全鏈路測試留言 ✨";

        MvcResult result = mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(content)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").isNumber())
                .andReturn();

        String body = result.getResponse().getContentAsString();
        Long commentId = Long.valueOf(body.replaceAll("\\D+", ""));

        var row = jdbcTemplate.queryForMap(
                "SELECT user_id, post_id, content FROM `comment` WHERE comment_id = ?", commentId);

        // 作者取自 principal 而非請求主體
        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(CURRENT_USER.userId());
        assertThat(((Number) row.get("post_id")).longValue()).isEqualTo(1L);
        assertThat(row.get("content")).isEqualTo(content);
    }

    @Test
    @DisplayName("對已軟刪除的發文新增留言：404（SP 的 SIGNAL 一路轉譯為 HTTP 狀態碼）")
    void returnsNotFoundForSoftDeletedPost() throws Exception {
        mockMvc.perform(post("/api/posts/" + softDeletedPostId() + "/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("不該寫入")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("發文不存在或已被刪除"));
    }

    @Test
    @DisplayName("對不存在的發文新增留言：404")
    void returnsNotFoundForMissingPost() throws Exception {
        mockMvc.perform(post("/api/posts/999999/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("不該寫入")))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("超過 500 字：400，且在進資料庫前就被擋下")
    void rejectsTooLongContentBeforeReachingDatabase() throws Exception {
        Long before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `comment`", Long.class);

        mockMvc.perform(post("/api/posts/1/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("字".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());

        // 應用層驗證的價值：不讓 MySQL 的 Data too long 成為使用者看到的錯誤
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM `comment`", Long.class))
                .isEqualTo(before);
    }
}
