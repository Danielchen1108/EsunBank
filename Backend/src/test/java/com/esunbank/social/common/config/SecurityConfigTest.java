package com.esunbank.social.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.RequestBuilder;

import com.esunbank.social.business.service.UserService;
import com.esunbank.social.common.security.AuthenticatedUser;
import com.esunbank.social.common.security.JwtTokenService;
import com.esunbank.social.presentation.controller.AuthController;

/**
 * 授權規則（F003 AC-3、AC-4、AC-5）。
 *
 * <p>對應題目 §2「確保只有登入的使用者可以發文或留言」。
 *
 * <p>本測試涵蓋 F004 發文與 F005 留言的路徑，但**不依賴其控制器是否已實作**——
 * Spring Security 的過濾鏈在 DispatcherServlet 之前執行，未登入的請求在路由之前就被擋下。
 * 因此「已登入」情境只斷言「不是 401／403」，不斷言後續狀態碼，
 * 避免 F004／F005 上線後本測試反而失敗。
 */
@WebMvcTest(AuthController.class)
@Import(SecurityConfig.class)
class SecurityConfigTest {

    @Autowired
    private MockMvc mockMvc;

    /** 與過濾鏈同一個 bean——用它簽發的憑證才驗得過。 */
    @Autowired
    private JwtTokenService jwtTokenService;

    @org.springframework.test.context.bean.override.mockito.MockitoBean
    private UserService userService;

    private String bearer() {
        return "Bearer " + jwtTokenService.issue(new AuthenticatedUser(7L, "0912345678"));
    }

    private void expectUnauthorized(RequestBuilder request) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").exists());
    }

    private void expectNotBlockedByAuthorization(RequestBuilder request) throws Exception {
        int status = mockMvc.perform(request).andReturn().getResponse().getStatus();
        assertThat(status).isNotIn(401, 403);
    }

    @Test
    @DisplayName("未登入不能發文（AC-3）")
    void anonymousCannotCreatePost() throws Exception {
        expectUnauthorized(post("/api/posts"));
    }

    @Test
    @DisplayName("未登入不能讀取發文列表——deny by default，未列白名單者一律要求登入")
    void anonymousCannotListPosts() throws Exception {
        expectUnauthorized(get("/api/posts"));
    }

    @Test
    @DisplayName("未登入不能新增留言（AC-4）")
    void anonymousCannotComment() throws Exception {
        expectUnauthorized(post("/api/posts/1/comments"));
    }

    @Test
    @DisplayName("已登入可存取發文與留言端點（AC-5）")
    void authenticatedUserPassesAuthorization() throws Exception {
        expectNotBlockedByAuthorization(post("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer()));
        expectNotBlockedByAuthorization(get("/api/posts").header(HttpHeaders.AUTHORIZATION, bearer()));
        expectNotBlockedByAuthorization(
                post("/api/posts/1/comments").header(HttpHeaders.AUTHORIZATION, bearer()));
    }

    @Test
    @DisplayName("偽造的憑證無法通過——回 401")
    void forgedTokenIsRejected() throws Exception {
        expectUnauthorized(post("/api/posts").header(HttpHeaders.AUTHORIZATION, "Bearer forged.token.value"));
    }

    @Test
    @DisplayName("401 回應帶 WWW-Authenticate: Bearer，告知用戶端該用哪種憑證")
    void unauthorizedResponseDeclaresScheme() throws Exception {
        mockMvc.perform(get("/api/posts"))
                .andExpect(status().isUnauthorized())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers
                        .header().string(HttpHeaders.WWW_AUTHENTICATE, "Bearer"));
    }

    @Test
    @DisplayName("健康檢查與註冊、登入端點免驗證")
    void publicEndpointsRemainOpen() throws Exception {
        // 健康檢查控制器不在本 @WebMvcTest 範圍內，故預期 404（路由不到）而非 401（被擋）
        expectNotBlockedByAuthorization(get("/api/health"));
        expectNotBlockedByAuthorization(post("/api/auth/register"));
        expectNotBlockedByAuthorization(post("/api/auth/login"));
    }
}
