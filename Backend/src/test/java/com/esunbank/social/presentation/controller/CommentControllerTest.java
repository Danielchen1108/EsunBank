package com.esunbank.social.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.esunbank.social.business.service.CommentCreateCommand;
import com.esunbank.social.business.service.CommentService;
import com.esunbank.social.common.exception.CommentTargetPostNotFoundException;
import com.esunbank.social.common.security.AuthenticatedUser;

/**
 * 新增留言端點（F005 AC-1、AC-3、AC-4）。
 *
 * <p><b>為何 {@code addFilters = false}：</b>F003 的 JWT 過濾鏈與本功能並行開發，
 * 本測試不應依賴其完成度。改以 principal 直接注入，驗證控制器如何「使用」已驗證身分；
 * 「未登入被擋下」（AC-2）由 {@code SecurityConfig} 的 deny-by-default 負責，
 * 於 F003 的測試與端到端驗證涵蓋，見 {@code F005-API.md} § 驗證。
 */
@WebMvcTest(CommentController.class)
@AutoConfigureMockMvc(addFilters = false)
class CommentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CommentService commentService;

    private static final AuthenticatedUser CURRENT_USER = new AuthenticatedUser(7L, "0912345678");

    /**
     * 模擬 F003 過濾鏈驗證成功後放入 {@code SecurityContext} 的 principal。
     *
     * <p>{@code addFilters = false} 跳過的正是會填入 {@code SecurityContext} 的過濾器，
     * 因此必須自行放入，否則 {@code @AuthenticationPrincipal} 解析出的是 null。
     */
    @BeforeEach
    void authenticateCurrentUser() {
        TestSecurityContextHolder.setAuthentication(
                new UsernamePasswordAuthenticationToken(CURRENT_USER, null, List.of()));
    }

    @AfterEach
    void clearCurrentUser() {
        TestSecurityContextHolder.clearContext();
    }

    /** 手工組 JSON 而不借助序列化工具，使測試送出的 request body 一望即知。 */
    private static String bodyWith(String content) {
        String escaped = content.replace("\\", "\\\\").replace("\"", "\\\"");
        return "{\"content\": \"" + escaped + "\"}";
    }

    @Test
    @DisplayName("新增留言成功回 201 與 commentId")
    void createsComment() throws Exception {
        when(commentService.create(any(CommentCreateCommand.class))).thenReturn(42L);

        mockMvc.perform(post("/api/posts/3/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("照片拍得不錯")))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.commentId").value(42));
    }

    @Test
    @DisplayName("留言作者取自已驗證身分，發文取自路徑——皆不由請求主體指定")
    void takesAuthorFromPrincipalAndPostFromPath() throws Exception {
        when(commentService.create(any(CommentCreateCommand.class))).thenReturn(42L);

        mockMvc.perform(post("/api/posts/3/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("照片拍得不錯")))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(CommentCreateCommand.class);
        verify(commentService).create(captor.capture());

        assertThat(captor.getValue().userId()).isEqualTo(7L);
        assertThat(captor.getValue().postId()).isEqualTo(3L);
        assertThat(captor.getValue().content()).isEqualTo("照片拍得不錯");
    }

    @Test
    @DisplayName("留言內容空白回 400 並指出欄位")
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/posts/3/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("   ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @DisplayName("留言內容超過 500 字回 400（ADR-005）")
    void rejectsTooLongContent() throws Exception {
        mockMvc.perform(post("/api/posts/3/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("字".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @DisplayName("目標發文不存在或已軟刪除回 404")
    void rejectsMissingTargetPost() throws Exception {
        when(commentService.create(any(CommentCreateCommand.class)))
                .thenThrow(new CommentTargetPostNotFoundException(999L));

        mockMvc.perform(post("/api/posts/999/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith("對不存在的發文留言")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("SQL Injection 字串原樣傳給業務層，不在展示層改寫")
    void passesInjectionStringVerbatim() throws Exception {
        when(commentService.create(any(CommentCreateCommand.class))).thenReturn(42L);

        String hostile = "'); DROP TABLE `comment`; --";

        mockMvc.perform(post("/api/posts/3/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(hostile)))
                .andExpect(status().isCreated());

        var captor = org.mockito.ArgumentCaptor.forClass(CommentCreateCommand.class);
        verify(commentService).create(captor.capture());
        assertThat(captor.getValue().content()).isEqualTo(hostile);
    }

    @Test
    @DisplayName("XSS 輸入被當作純文字接收，不在寫入時改寫")
    void storesUserInputVerbatim() throws Exception {
        when(commentService.create(any(CommentCreateCommand.class))).thenReturn(42L);

        String hostile = "<script>alert(1)</script>";

        mockMvc.perform(post("/api/posts/3/comments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyWith(hostile)))
                .andExpect(status().isCreated());

        // XSS 的防護位置在輸出端（Vue 插值自動跳脫），不在寫入端改寫使用者資料。
        // 見 F005-API.md § 安全考量。
        var captor = org.mockito.ArgumentCaptor.forClass(CommentCreateCommand.class);
        verify(commentService).create(captor.capture());
        assertThat(captor.getValue().content()).isEqualTo(hostile);
    }
}
