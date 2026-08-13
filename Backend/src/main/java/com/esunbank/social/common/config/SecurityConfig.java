package com.esunbank.social.common.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import com.esunbank.social.common.security.JwtAuthenticationEntryPoint;
import com.esunbank.social.common.security.JwtAuthenticationFilter;
import com.esunbank.social.common.security.JwtTokenService;

/**
 * 安全設定（共用層）。
 *
 * <p>對應決策 ADR-003：登入驗證採 Spring Security + JWT。
 *
 * <p>本類別是題目 §2「確保只有登入的使用者可以發文或留言」的落地點：
 * 授權規則集中於此，各控制器不需自行檢查身分。
 */
@Configuration
public class SecurityConfig {

    /**
     * 密碼編碼器。
     *
     * <p>對應題目第 2 頁 User 表：「密碼請加鹽(salt)並經雜湊(Hash)後儲存，避免明碼外洩」。
     *
     * <p>{@link BCryptPasswordEncoder} 每次編碼會自動產生隨機 salt 並內嵌於輸出中，
     * 因此無須另建 salt 欄位。輸出固定 60 字元，對應資料表 {@code user.password}
     * 的 {@code VARCHAR(72)} 設計（F001 AC-12）。
     *
     * <p>登入時以 {@code matches(明碼, 雜湊值)} 比對——BCrypt 為單向函式，
     * 雜湊值無法還原成明碼，只能重新雜湊後比較（F003 AC-2）。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * JWT 簽發與驗證元件。
     *
     * <p>金鑰不寫死在原始碼中，以環境變數注入（與資料庫帳密相同做法）：
     *
     * <pre>{@code export APP_JWT_SECRET=至少32位元組的隨機字串}</pre>
     *
     * <p>未設定時退回隨機金鑰並記錄警告，見 {@link JwtTokenService}。
     */
    @Bean
    public JwtTokenService jwtTokenService(@Value("${app.jwt.secret:}") String secret) {
        return new JwtTokenService(secret);
    }

    /**
     * 安全過濾鏈。
     *
     * <p><b>驗證方式（F003）：</b>移除骨架階段的 HTTP Basic，改以 JWT 過濾器驗證。
     * HTTP Basic 每次請求都要帶明碼帳密，且會觸發瀏覽器原生登入視窗，
     * 不適用於題目 §6 指定的 Vue.js 前後端分離架構。
     *
     * <p><b>白名單（permitAll）：</b>
     * <ul>
     *   <li>{@code GET /api/health} — 連線狀態檢查，不含使用者資料</li>
     *   <li>{@code POST /api/auth/register} — 使用者此時尚無帳號（題目 §1）</li>
     *   <li>{@code POST /api/auth/login} — 登入本身若需先登入即成死結</li>
     * </ul>
     *
     * <p><b>其餘一律要求登入（deny by default）：</b>含 {@code /api/posts/**}
     * 與 {@code /api/posts/*}{@code /comments}，即題目 §2 明文要求保護的發文與留言。
     * 採「預設拒絕」而非逐條列舉保護對象——日後新增端點時，遺漏設定的後果是被擋下
     * 而非被公開，失誤方向較安全。
     *
     * <p>CSRF 停用的理由：本服務為無狀態 RESTful API（題目 §6），
     * 驗證憑證以 Authorization 標頭傳遞而非 Cookie，不存在 CSRF 的攻擊前提。
     *
     * <p>Session 設為 {@code STATELESS}：伺服器不保存登入狀態，
     * 每次請求都由 JWT 自行證明身分，符合 REST 的無狀態原則（ADR-003 理由 1）。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtTokenService jwtTokenService) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                .requestMatchers(HttpMethod.GET, "/api/health").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/register").permitAll()
                .requestMatchers(HttpMethod.POST, "/api/auth/login").permitAll()
                .anyRequest().authenticated()
            )
            // 放在帳密驗證過濾器之前：本服務不使用表單登入，此位置即為驗證環節的起點
            .addFilterBefore(new JwtAuthenticationFilter(jwtTokenService),
                    UsernamePasswordAuthenticationFilter.class)
            .exceptionHandling(handling -> handling
                    .authenticationEntryPoint(new JwtAuthenticationEntryPoint()));

        return http.build();
    }

    /**
     * 明確宣告 {@link AuthenticationManager}，使 Spring Boot 不再自動建立預設使用者。
     *
     * <p>Spring Boot 的 {@code UserDetailsServiceAutoConfiguration} 在容器中缺少
     * {@code AuthenticationManager} / {@code UserDetailsService} 時，會建立一個
     * 帶隨機密碼的記憶體使用者並印在啟動日誌中。本服務的身分驗證全由 JWT 過濾器完成，
     * 該預設使用者永遠不會被用到，留著只是多一組沒人管理的憑證。
     *
     * <p>本 manager 一律拒絕：若有程式碼意外走到帳密驗證流程，會立即失敗而非默默通過。
     */
    @Bean
    public AuthenticationManager authenticationManager() {
        return authentication -> {
            throw new AuthenticationServiceException(
                    "本服務不使用 AuthenticationManager，身分驗證由 JwtAuthenticationFilter 完成");
        };
    }
}
