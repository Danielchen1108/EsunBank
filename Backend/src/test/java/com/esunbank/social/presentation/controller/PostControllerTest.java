package com.esunbank.social.presentation.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.test.context.TestSecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.esunbank.social.business.service.Comment;
import com.esunbank.social.business.service.Post;
import com.esunbank.social.business.service.PostCreateCommand;
import com.esunbank.social.business.service.PostNotFoundException;
import com.esunbank.social.business.service.PostService;
import com.esunbank.social.business.service.PostUpdateCommand;
import com.esunbank.social.common.security.AuthenticatedUser;

/**
 * 發文端點（F004 AC-1、AC-3、AC-4、AC-5、AC-7、AC-8）。
 *
 * <p><b>為何 {@code addFilters = false}：</b>F003 登入驗證的 JWT 過濾鏈尚在開發中。
 * 本測試驗證的是控制器行為，不是過濾鏈設定——後者屬 F003 職責，於該功能的測試中驗證。
 * 目前使用者改以 {@code authentication(...)} 直接注入
 * {@link AuthenticatedUser}（F003／F004／F005 的共用契約）。
 *
 * <p>因此 <b>AC-2「未登入者無法新增發文」不在本測試涵蓋範圍</b>：
 * 該行為由 {@code SecurityConfig} 的 deny-by-default 與 F003 的過濾鏈保證。
 */
@WebMvcTest(PostController.class)
@AutoConfigureMockMvc(addFilters = false)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private PostService postService;

    @AfterEach
    void clearSecurityContext() {
        TestSecurityContextHolder.clearContext();
    }

    /**
     * 模擬 F003 過濾鏈驗證成功後放入 SecurityContext 的 principal。
     *
     * <p>直接寫入 {@link TestSecurityContextHolder} 而非用
     * {@code SecurityMockMvcRequestPostProcessors.authentication(...)}：後者把 Authentication
     * 存進 request，交由過濾鏈載入——本測試以 {@code addFilters = false} 跳過過濾鏈，
     * 沒有任何一環會去讀它。{@code @AuthenticationPrincipal} 實際讀的是
     * {@code SecurityContextHolder}，故直接設在該處。
     */
    private RequestPostProcessor loginAs(long userId) {
        AuthenticatedUser currentUser = new AuthenticatedUser(userId, "0912345678");

        return request -> {
            TestSecurityContextHolder.setAuthentication(
                    new UsernamePasswordAuthenticationToken(currentUser, null, List.of()));
            return request;
        };
    }

    private Post samplePost(long postId, long userId, String content) {
        return samplePost(postId, userId, content, List.of());
    }

    private Post samplePost(long postId, long userId, String content, List<Comment> comments) {
        return new Post(
                postId, userId, "陳大文", content, LocalDateTime.of(2026, 8, 13, 10, 0), comments);
    }

    private Comment sampleComment(long commentId, long userId, String content, int minute) {
        return new Comment(
                commentId, userId, "林小明", content, LocalDateTime.of(2026, 8, 13, 10, minute));
    }

    @Test
    @DisplayName("新增發文回 201 與 postId，userId 取自登入者而非請求內容（AC-1、AC-6）")
    void createsPost() throws Exception {
        when(postService.create(any(PostCreateCommand.class))).thenReturn(9L);

        mockMvc.perform(post("/api/posts")
                        .with(loginAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "今天天氣真好" }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.postId").value(9));

        ArgumentCaptor<PostCreateCommand> captor = ArgumentCaptor.forClass(PostCreateCommand.class);
        verify(postService).create(captor.capture());
        assertThat(captor.getValue().userId()).isEqualTo(2L);
        assertThat(captor.getValue().content()).isEqualTo("今天天氣真好");
    }

    @Test
    @DisplayName("新增發文：內容為空回 400 並指出欄位")
    void rejectsBlankContent() throws Exception {
        mockMvc.perform(post("/api/posts")
                        .with(loginAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "  " }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @DisplayName("新增發文：內容超過 2000 字元回 400（ADR-005）")
    void rejectsOversizedContent() throws Exception {
        String body = "{ \"content\": \"" + "a".repeat(2001) + "\" }";

        mockMvc.perform(post("/api/posts")
                        .with(loginAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.content").exists());
    }

    @Test
    @DisplayName("列出所有發文回 200 與陣列（AC-3）")
    void listsPosts() throws Exception {
        when(postService.listAll()).thenReturn(List.of(samplePost(1L, 1L, "第一篇"), samplePost(2L, 3L, "第二篇")));

        mockMvc.perform(get("/api/posts").with(loginAs(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].postId").value(1))
                .andExpect(jsonPath("$[0].userName").value("陳大文"))
                .andExpect(jsonPath("$[0].content").value("第一篇"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                // 回應不含 image：欄位保留於 schema（需求規格），但無上傳功能故 API 不開放
                // （SCOPE-BOUNDARY.md R-3）。回一個永遠為 null 的欄位只會誤導前端。
                .andExpect(jsonPath("$[0].image").doesNotExist());
    }

    @Test
    @DisplayName("列出所有發文一併帶出留言，欄位齊全且順序保留（D-13）")
    void listIncludesComments() throws Exception {
        // 本測試取代原本的 listDoesNotIncludeComments——OQ-1 原決定不帶出，
        // 後由 owner 明示追加（D-13）。舊測試把該決定固化成規格，故一併反轉。
        when(postService.listAll()).thenReturn(List.of(samplePost(1L, 1L, "第一篇", List.of(
                sampleComment(11L, 2L, "先留的", 40),
                sampleComment(12L, 3L, "後留的", 45)))));

        mockMvc.perform(get("/api/posts").with(loginAs(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comments.length()").value(2))
                .andExpect(jsonPath("$[0].comments[0].commentId").value(11))
                .andExpect(jsonPath("$[0].comments[0].userId").value(2))
                .andExpect(jsonPath("$[0].comments[0].userName").value("林小明"))
                .andExpect(jsonPath("$[0].comments[0].content").value("先留的"))
                .andExpect(jsonPath("$[0].comments[0].createdAt").exists())
                // 業務層傳來的順序原樣輸出：留言是對話，順序本身就是資訊
                .andExpect(jsonPath("$[0].comments[1].content").value("後留的"))
                // 留言巢狀於所屬發文之下，postId 是分組的鍵而非留言本身的屬性，
                // 重複帶出只會多一個可能與外層不一致的欄位（見 CommentResponse）
                .andExpect(jsonPath("$[0].comments[0].postId").doesNotExist());
    }

    @Test
    @DisplayName("沒有留言的發文回傳空陣列而非 null——前端不必多判一次")
    void listReturnsEmptyArrayForPostWithoutComments() throws Exception {
        when(postService.listAll()).thenReturn(List.of(samplePost(1L, 1L, "沒人留言")));

        mockMvc.perform(get("/api/posts").with(loginAs(2L)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].comments").isArray())
                .andExpect(jsonPath("$[0].comments").isEmpty());
    }

    @Test
    @DisplayName("編輯發文回 200 與更新後內容（AC-4）")
    void updatesPost() throws Exception {
        when(postService.update(any(PostUpdateCommand.class))).thenReturn(samplePost(1L, 1L, "修改後內容"));

        mockMvc.perform(put("/api/posts/1")
                        .with(loginAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "修改後內容" }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.postId").value(1))
                .andExpect(jsonPath("$.content").value("修改後內容"));
    }

    @Test
    @DisplayName("編輯不存在或已刪除的發文回 404")
    void updateMissingPostReturnsNotFound() throws Exception {
        when(postService.update(any(PostUpdateCommand.class))).thenThrow(new PostNotFoundException(99L));

        mockMvc.perform(put("/api/posts/99")
                        .with(loginAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "內容" }
                                """))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    @DisplayName("刪除發文回 204（AC-5）")
    void deletesPost() throws Exception {
        mockMvc.perform(delete("/api/posts/1").with(loginAs(2L)))
                .andExpect(status().isNoContent());

        verify(postService).delete(1L);
    }

    @Test
    @DisplayName("刪除不存在或已刪除的發文回 404")
    void deleteMissingPostReturnsNotFound() throws Exception {
        doThrow(new PostNotFoundException(99L)).when(postService).delete(99L);

        mockMvc.perform(delete("/api/posts/99").with(loginAs(2L)))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("任何登入者皆可編輯與刪除他人發文——BG-4 刻意決策，非授權遺漏")
    void anyAuthenticatedUserMayEditOthersPosts() throws Exception {
        // 發文屬於 userId=1，操作者為 userId=7。需求 §2 的驗證範圍僅涵蓋「發文或留言」，
        // 未涵蓋編輯與刪除；owner 於 F004-REQ.md BG-4 裁決不實作發文者身分檢查。
        when(postService.update(any(PostUpdateCommand.class))).thenReturn(samplePost(1L, 1L, "被別人改掉"));

        mockMvc.perform(put("/api/posts/1")
                        .with(loginAs(7L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "被別人改掉" }
                                """))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/posts/1").with(loginAs(7L)))
                .andExpect(status().isNoContent());
    }

    @Test
    @DisplayName("XSS 輸入原樣接收，不在寫入時改寫（AC-8）")
    void storesUserInputVerbatim() throws Exception {
        when(postService.create(any(PostCreateCommand.class))).thenReturn(9L);

        mockMvc.perform(post("/api/posts")
                        .with(loginAs(2L))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                { "content": "<script>alert(1)</script>" }
                                """))
                .andExpect(status().isCreated());

        ArgumentCaptor<PostCreateCommand> captor = ArgumentCaptor.forClass(PostCreateCommand.class);
        verify(postService).create(captor.capture());
        // XSS 的防護位置在輸出端（Vue 插值自動跳脫），不在寫入端改寫使用者資料。
        // 見 F004-API.md § 安全考量；前端不得對 content 使用 v-html。
        assertThat(captor.getValue().content()).isEqualTo("<script>alert(1)</script>");
    }
}
