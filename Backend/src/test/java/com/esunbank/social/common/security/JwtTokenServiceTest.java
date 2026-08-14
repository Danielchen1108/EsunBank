package com.esunbank.social.common.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.SecretKey;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * JWT 簽發與驗證。
 *
 * <p>本測試的核心是證明：**憑證無法被偽造或竄改**。
 * 需求要求「確保只有登入的使用者可以發文或留言」——若簽章可繞過，該保證即失效。
 *
 * <p>另驗證 token **不含 {@code exp}**：不實作有效期，
 * 這是刻意的範圍決策，須以測試釘住，避免日後被無意識加上。
 */
class JwtTokenServiceTest {

    /** HS256 要求金鑰至少 256 bit（32 位元組），以下字串為 40 位元組。 */
    private static final String SECRET = "esunbank-social-test-secret-key-32bytes!";

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET);

    private final AuthenticatedUser user = new AuthenticatedUser(7L, "0912345678");

    private static SecretKey key(String secret) {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("簽發的 token 可還原為同一個 AuthenticatedUser")
    void issuedTokenRoundTrips() {
        String token = jwtTokenService.issue(user);

        Optional<AuthenticatedUser> verified = jwtTokenService.verify(token);

        assertThat(verified).contains(user);
    }

    @Test
    @DisplayName("token 不含 exp——不實作有效期")
    void tokenHasNoExpiration() {
        String token = jwtTokenService.issue(user);

        Claims claims = Jwts.parser()
                .verifyWith(key(SECRET))
                .build()
                .parseSignedClaims(token)
                .getPayload();

        assertThat(claims.getExpiration()).isNull();
        assertThat(claims.getSubject()).isEqualTo("7");
        assertThat(claims.get("phone", String.class)).isEqualTo("0912345678");
    }

    @Test
    @DisplayName("內容被竄改的 token 驗證失敗")
    void rejectsTamperedToken() {
        String token = jwtTokenService.issue(user);
        // 竄改 payload 段（第二段）的最後一個字元，簽章隨即對不上
        String[] parts = token.split("\\.");
        String tampered = parts[0] + "." + parts[1].substring(0, parts[1].length() - 1) + "X." + parts[2];

        assertThat(jwtTokenService.verify(tampered)).isEmpty();
    }

    @Test
    @DisplayName("改寫 payload 冒充他人身分——簽章涵蓋 payload，驗證失敗")
    void rejectsForgedIdentity() {
        String[] parts = jwtTokenService.issue(user).split("\\.");
        String forgedPayload = Base64.getUrlEncoder().withoutPadding().encodeToString(
                "{\"sub\":\"999\",\"phone\":\"0900000000\"}".getBytes(StandardCharsets.UTF_8));

        assertThat(jwtTokenService.verify(parts[0] + "." + forgedPayload + "." + parts[2])).isEmpty();
    }

    @Test
    @DisplayName("移除簽章的 token 驗證失敗——不接受未簽章憑證")
    void rejectsUnsignedToken() {
        String[] parts = jwtTokenService.issue(user).split("\\.");

        assertThat(jwtTokenService.verify(parts[0] + "." + parts[1] + ".")).isEmpty();
    }

    @Test
    @DisplayName("以其他金鑰簽發的 token 驗證失敗")
    void rejectsTokenSignedWithAnotherKey() {
        String foreign = Jwts.builder()
                .subject("7")
                .claim("phone", "0912345678")
                .signWith(key("another-secret-key-that-is-32-bytes-long!"), Jwts.SIG.HS256)
                .compact();

        assertThat(jwtTokenService.verify(foreign)).isEmpty();
    }

    @Test
    @DisplayName("不成形的字串驗證失敗，而非拋出例外")
    void rejectsMalformedToken() {
        assertThat(jwtTokenService.verify("not-a-jwt")).isEmpty();
        assertThat(jwtTokenService.verify("")).isEmpty();
    }

    @Test
    @DisplayName("未設定金鑰時改用隨機金鑰，仍可正常簽發與驗證")
    void fallsBackToRandomKeyWhenSecretIsBlank() {
        JwtTokenService withRandomKey = new JwtTokenService("");

        assertThat(withRandomKey.verify(withRandomKey.issue(user))).contains(user);
        // 隨機金鑰與設定金鑰互不相通——印證確實各自獨立
        assertThat(withRandomKey.verify(jwtTokenService.issue(user))).isEmpty();
    }
}
