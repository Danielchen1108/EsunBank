package com.esunbank.social.common.security;

import java.io.IOException;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 未登入時的回應（共用層）。
 *
 * <p>對應題目 §2：未登入者存取發文或留言端點時的回應內容。
 *
 * <p><b>為何需要這個類別：</b>Spring Security 在未設定進入點時，預設會導向登入頁
 * 或回 403。本服務是 RESTful API（題目 §6），應回 JSON 而非 HTML，
 * 且「未提供憑證」語意上是 401 而非 403。
 *
 * <p><b>401 而非 403 的判定（RFC 9110）：</b>401 表示「缺少或無效的驗證憑證」，
 * 403 表示「身分已知但無權限」。本案未登入即屬前者。且題目未定義權限分級，
 * 已登入者一律有相同權限，故 403 在本系統中無適用情境。
 */
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    @Override
    public void commence(HttpServletRequest request,
                         HttpServletResponse response,
                         AuthenticationException authException) throws IOException {

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        // RFC 9110 §15.5.2：401 回應須以 WWW-Authenticate 告知用戶端應採用的憑證方式
        response.setHeader(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");

        // 訊息不透露失敗細節（憑證過期／格式錯誤／簽章不符）——避免給探測者線索。
        // 格式與 GlobalExceptionHandler 一致，前端可用同一套邏輯解析。
        response.getWriter().write("{\"message\":\"請先登入\"}");
    }
}
