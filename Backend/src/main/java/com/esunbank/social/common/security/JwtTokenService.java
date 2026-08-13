package com.esunbank.social.common.security;

import java.nio.charset.StandardCharsets;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 簽發與驗證（共用層）。
 *
 * <p>對應決策 ADR-003：登入驗證採 Spring Security + JWT。
 *
 * <p><b>為何是 JWT 而非 HttpSession：</b>題目 §6 要求 RESTful 風格，
 * REST 的無狀態特性與 JWT 相符；且前端為 Vue.js 的前後端分離架構（ADR-003 理由 1、2）。
 *
 * <p><b>刻意不設 {@code exp}：</b>題目未提及憑證有效期，owner 裁決 BG-3 不實作
 * （見 {@code SCOPE-BOUNDARY.md} Out of Scope）。憑證一經簽發即長期有效，
 * 風險已於 {@code F003-IMPACT.md} 明示接受。同理不實作更新（refresh）與登出。
 *
 * <p>本類別由 {@code SecurityConfig} 建立為 bean 而非標註 {@code @Service}——
 * 金鑰來源屬設定範疇，集中於設定類別可讓「金鑰從哪來」在一處看完。
 */
public class JwtTokenService {

    private static final Logger log = LoggerFactory.getLogger(JwtTokenService.class);

    /** 手機號碼（登入帳號）的自訂 claim 名稱。 */
    private static final String CLAIM_PHONE = "phone";

    private final SecretKey key;

    /**
     * @param secret 簽章金鑰。HS256 要求至少 32 位元組；
     *               留空時改用啟動時隨機產生的金鑰（見下方說明）
     */
    public JwtTokenService(String secret) {
        if (secret == null || secret.isBlank()) {
            // 不內建預設金鑰——寫死的金鑰一旦進版控，任何人都能自行簽發合法憑證。
            // 改為隨機產生：安全但重啟後既有憑證失效，故以 WARN 提醒須設定 APP_JWT_SECRET。
            this.key = Jwts.SIG.HS256.key().build();
            log.warn("未設定 app.jwt.secret（環境變數 APP_JWT_SECRET），本次啟動使用隨機金鑰；"
                    + "重新啟動後先前簽發的憑證將無法驗證。");
        } else {
            this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        }
    }

    /**
     * 簽發憑證。
     *
     * <p>只放 {@code userId} 與 {@code phone}——JWT 的 payload 僅經 Base64 編碼、未加密，
     * 任何人都讀得到，故不放密碼雜湊、Email 等其他 PII。
     */
    public String issue(AuthenticatedUser user) {
        return Jwts.builder()
                .subject(String.valueOf(user.userId()))
                .claim(CLAIM_PHONE, user.phone())
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * 驗證憑證並取出使用者。
     *
     * <p>{@code verifyWith} 會檢查簽章：憑證內容一經竄改，或由其他金鑰簽發，
     * 皆無法通過——這是題目 §2「只有登入的使用者可以發文或留言」的技術保證。
     *
     * <p>回傳 {@link Optional} 而非拋例外：呼叫端（過濾器）對「無憑證」與「憑證無效」
     * 的處理相同，都是不設定身分並交由授權規則決定，無須以例外區分。
     *
     * @return 驗證通過的使用者；憑證無效時為空
     */
    public Optional<AuthenticatedUser> verify(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            return Optional.of(new AuthenticatedUser(
                    Long.valueOf(claims.getSubject()),
                    claims.get(CLAIM_PHONE, String.class)));
        } catch (JwtException | IllegalArgumentException e) {
            // IllegalArgumentException 涵蓋 null/空字串與 subject 非數字的情形
            return Optional.empty();
        }
    }
}
