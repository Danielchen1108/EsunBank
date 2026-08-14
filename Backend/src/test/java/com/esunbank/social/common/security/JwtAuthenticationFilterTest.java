package com.esunbank.social.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.util.WebUtils;

import jakarta.servlet.DispatcherType;

/**
 * JWT 驗證過濾器。
 *
 * <p>本測試釘住 ／／的共用契約：驗證成功後放進 {@code SecurityContext}
 * 的 principal 必須是 {@link AuthenticatedUser}——發文與 留言以
 * {@code @AuthenticationPrincipal AuthenticatedUser} 取得目前使用者，型別不符即整批失效。
 *
 * <p>驗證失敗時**不直接回 401**，而是留空 {@code SecurityContext} 繼續往下走：
 * 該擋或該放由 {@code SecurityConfig} 的授權規則決定，過濾器不重複實作授權判斷。
 */
class JwtAuthenticationFilterTest {

    private static final String SECRET = "esunbank-social-test-secret-key-32bytes!";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET);

    private final JwtAuthenticationFilter filter = new JwtAuthenticationFilter(jwtTokenService);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private MockFilterChain doFilterWith(String authorizationHeader) throws Exception {
        return doFilterWith(authorizationHeader, DispatcherType.REQUEST);
    }

    private MockFilterChain doFilterWith(String authorizationHeader, DispatcherType dispatcherType)
            throws Exception {

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/posts");
        request.setDispatcherType(dispatcherType);
        if (dispatcherType == DispatcherType.ERROR) {
            // OncePerRequestFilter 判斷「是否為錯誤轉發」看的是這個屬性而非 DispatcherType
            request.setAttribute(WebUtils.ERROR_REQUEST_URI_ATTRIBUTE, "/api/posts");
        }
        if (authorizationHeader != null) {
            request.addHeader("Authorization", authorizationHeader);
        }
        MockFilterChain chain = new MockFilterChain();
        filter.doFilter(request, new MockHttpServletResponse(), chain);
        return chain;
    }

    @Test
    @DisplayName("有效 token 使 principal 成為 AuthenticatedUser")
    void validTokenPopulatesAuthenticatedUserPrincipal() throws Exception {
        String token = jwtTokenService.issue(new AuthenticatedUser(7L, "0912345678"));

        doFilterWith("Bearer " + token);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        assertThat(authentication).isNotNull();
        assertThat(authentication.isAuthenticated()).isTrue();
        assertThat(authentication.getPrincipal())
                .isInstanceOf(AuthenticatedUser.class)
                .isEqualTo(new AuthenticatedUser(7L, "0912345678"));
    }

    @Test
    @DisplayName("無 Authorization 標頭時不設定身分，且請求繼續往下走")
    void missingHeaderLeavesContextEmpty() throws Exception {
        MockFilterChain chain = doFilterWith(null);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).as("請求須繼續往下傳遞，由授權規則決定是否放行").isNotNull();
    }

    @Test
    @DisplayName("無效 token 不設定身分，且請求繼續往下走")
    void invalidTokenLeavesContextEmpty() throws Exception {
        MockFilterChain chain = doFilterWith("Bearer this.is.not.valid");

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        assertThat(chain.getRequest()).isNotNull();
    }

    @Test
    @DisplayName("錯誤轉發（ERROR dispatch）時仍須驗證身分")
    void authenticatesOnErrorDispatch() throws Exception {
        // OncePerRequestFilter 預設跳過 ERROR dispatch。若照預設，
        // 已登入者遇到 404／500 時會在錯誤轉發中失去身分，
        // 被授權規則擋成 401——真正的錯誤原因就此被 401 蓋掉。
        String token = jwtTokenService.issue(new AuthenticatedUser(7L, "0912345678"));

        doFilterWith("Bearer " + token, DispatcherType.ERROR);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNotNull();
    }

    @Test
    @DisplayName("非 Bearer 格式的標頭一律略過")
    void ignoresNonBearerScheme() throws Exception {
        String token = jwtTokenService.issue(new AuthenticatedUser(7L, "0912345678"));

        doFilterWith("Basic " + token);

        assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
    }
}
