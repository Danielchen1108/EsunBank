package com.esunbank.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.esunbank.social.common.exception.InvalidCredentialsException;
import com.esunbank.social.common.security.AuthenticatedUser;
import com.esunbank.social.common.security.JwtTokenService;
import com.esunbank.social.data.repository.UserRepository;

/**
 * 登入業務邏輯。
 *
 * <p>本測試的核心是證明：**密碼以雜湊比對，不還原明碼**。
 * 需求規格的 User 表要求密碼加鹽雜湊後儲存，BCrypt 為單向函式，
 * 故驗證只能是「把輸入重新雜湊後比對」而非「解回明碼再比字串」。
 *
 * <p>不使用 mock 的 {@code PasswordEncoder}——改用真實的 {@link BCryptPasswordEncoder}，
 * 才能真正證明雜湊值與明碼比對得起來。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceLoginTest {

    private static final String SECRET = "esunbank-social-test-secret-key-32bytes!";

    @Mock
    private UserRepository userRepository;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    private final JwtTokenService jwtTokenService = new JwtTokenService(SECRET);

    private UserService service() {
        return new UserService(userRepository, encoder, jwtTokenService);
    }

    /** 資料庫中的既有帳號：明碼 Test1234，僅以雜湊值儲存。 */
    private UserRepository.UserCredentials storedUser() {
        return new UserRepository.UserCredentials(7L, "0912345678", "陳大文", encoder.encode("Test1234"));
    }

    @Test
    @DisplayName("密碼正確時簽發可還原為該使用者的憑證")
    void issuesTokenForCorrectPassword() {
        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(storedUser()));

        LoginResult result = service().login(new LoginCommand("0912345678", "Test1234"));

        assertThat(result.userId()).isEqualTo(7L);
        assertThat(jwtTokenService.verify(result.token()))
                .contains(new AuthenticatedUser(7L, "0912345678"));
    }

    @Test
    @DisplayName("密碼錯誤時拒絕登入")
    void rejectsWrongPassword() {
        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(storedUser()));

        assertThatThrownBy(() -> service().login(new LoginCommand("0912345678", "WrongPassword")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("手機號碼不存在時拒絕登入，且例外與密碼錯誤相同")
    void rejectsUnknownPhone() {
        when(userRepository.findByPhone("0900000000")).thenReturn(Optional.empty());

        // 兩種失敗原因共用同一個例外型別，使回應訊息無法用來探測帳號是否存在
        assertThatThrownBy(() -> service().login(new LoginCommand("0900000000", "Test1234")))
                .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("比對的是雜湊值——明碼與資料庫中的字串並不相等")
    void comparesAgainstHashNotPlaintext() {
        UserRepository.UserCredentials stored = storedUser();
        when(userRepository.findByPhone("0912345678")).thenReturn(Optional.of(stored));

        assertThat(stored.passwordHash()).isNotEqualTo("Test1234").startsWith("$2");

        // 明碼不等於儲存值，卻仍能登入成功——證明走的是 BCrypt 比對而非字串相等
        assertThat(service().login(new LoginCommand("0912345678", "Test1234")).token()).isNotBlank();
    }
}
