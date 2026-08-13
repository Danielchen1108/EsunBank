package com.esunbank.social.common.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;

/**
 * 安全設定（共用層）。
 *
 * <p>對應決策 ADR-003：登入驗證採 Spring Security + JWT。
 *
 * <p><b>目前狀態：R101 骨架階段。</b>
 * 本設定僅開放健康檢查端點，其餘一律要求驗證（deny by default）。
 * JWT 過濾鏈將於 F003 登入驗證實作時加入，屆時本類別會被擴充而非取代。
 *
 * <p>後續 F-code 的變更點：
 * <ul>
 *   <li>F002 使用者註冊 — 需將註冊端點加入公開清單</li>
 *   <li>F003 登入驗證 — 加入登入端點與 JWT 驗證過濾器</li>
 * </ul>
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
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    /**
     * 安全過濾鏈。
     *
     * <p>CSRF 停用的理由：本服務為無狀態 RESTful API（題目 §6），
     * 驗證憑證以 Authorization 標頭傳遞而非 Cookie，不存在 CSRF 的攻擊前提。
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/api/health").permitAll()
                .anyRequest().authenticated()
            )
            .httpBasic(basic -> {});

        return http.build();
    }
}
