package com.esunbank.social.common.security;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * JWT 驗證過濾器（共用層）。
 *
 * <p>對應需求 §2「確保只有登入的使用者可以發文或留言」。
 * 攔截點放在過濾鏈而非各控制器，是需求 §5「共用層」的落地——
 * F004 發文與 F005 留言不必各自實作身分檢查。
 *
 * <p><b>驗證失敗不直接回 401：</b>本過濾器只負責「若憑證有效就標記身分」，
 * 該擋或該放由 {@code SecurityConfig} 的授權規則判定。
 * 兩者分離的理由：登入與註冊端點本來就允許無憑證存取，
 * 若在此直接拒絕，白名單將形同虛設。
 *
 * <p><b>刻意不註冊為 {@code @Component}：</b>Spring Boot 會把容器中的
 * {@code Filter} bean 自動註冊到 Servlet 容器，導致它在安全過濾鏈之外
 * 被重複執行一次。改由 {@code SecurityConfig} 明確組裝進過濾鏈。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String AUTHORIZATION_HEADER = "Authorization";

    /** RFC 6750 定義的 Bearer 憑證格式：{@code Authorization: Bearer <token>}。 */
    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtTokenService jwtTokenService;

    public JwtAuthenticationFilter(JwtTokenService jwtTokenService) {
        this.jwtTokenService = jwtTokenService;
    }

    /**
     * 錯誤轉發（ERROR dispatch）時也要驗證身分。
     *
     * <p>{@link OncePerRequestFilter} 預設跳過錯誤轉發，但 Spring Security 的授權過濾器不會跳過。
     * 若照預設，已登入的使用者一旦遇到 404 或 500，Servlet 容器轉發到 {@code /error} 時
     * 身分已消失，授權規則會把它擋成 401——真正的錯誤原因就被 401 蓋掉，難以排查。
     */
    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return false;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {

        bearerToken(request)
                .flatMap(jwtTokenService::verify)
                .ifPresent(user -> authenticate(user, request));

        filterChain.doFilter(request, response);
    }

    private Optional<String> bearerToken(HttpServletRequest request) {
        String header = request.getHeader(AUTHORIZATION_HEADER);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            return Optional.empty();
        }
        return Optional.of(header.substring(BEARER_PREFIX.length()));
    }

    /**
     * 把使用者放進 {@code SecurityContext}。
     *
     * <p><b>principal 必須是 {@link AuthenticatedUser}</b>——這是 F003／F004／F005
     * 的共用契約，各功能以 {@code @AuthenticationPrincipal AuthenticatedUser} 取得目前使用者。
     *
     * <p>權限清單為空：需求未定義角色或權限分級，僅區分「已登入／未登入」。
     * 使用三參數建構子，Spring Security 會將此 token 視為已通過驗證。
     */
    private void authenticate(AuthenticatedUser user, HttpServletRequest request) {
        UsernamePasswordAuthenticationToken authentication =
                new UsernamePasswordAuthenticationToken(user, null, List.of());
        authentication.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));

        SecurityContextHolder.getContext().setAuthentication(authentication);
    }
}
