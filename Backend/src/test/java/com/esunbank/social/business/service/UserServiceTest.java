package com.esunbank.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import com.esunbank.social.common.exception.DuplicatePhoneException;
import com.esunbank.social.common.security.JwtTokenService;
import com.esunbank.social.data.repository.UserRepository;

/**
 * 註冊業務邏輯（F002 AC-3）。
 *
 * <p>本測試的核心是證明：**明碼密碼不會流入資料層**。
 * 需求規格明文要求「密碼請加鹽(salt)並經雜湊(Hash)後儲存，避免明碼外洩」。
 */
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @Captor
    private ArgumentCaptor<String> passwordCaptor;

    private UserService userService;

    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    /**
     * 註冊流程不使用 {@link JwtTokenService}（需求未要求註冊後自動登入），
     * 但它是 {@link UserService} 的建構子相依，故仍須提供一個實例。
     */
    private UserService service() {
        if (userService == null) {
            userService = new UserService(userRepository, encoder, new JwtTokenService(""));
        }
        return userService;
    }

    private RegisterCommand command() {
        return new RegisterCommand("0912345678", "陳大文", "alice@example.com", "Test1234", "自我介紹");
    }

    @Test
    @DisplayName("傳給資料層的密碼是雜湊值，不是明碼")
    void hashesPasswordBeforePersisting() {
        when(userRepository.register(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        service().register(command());

        org.mockito.Mockito.verify(userRepository)
                .register(anyString(), anyString(), anyString(), passwordCaptor.capture(), anyString());

        String persisted = passwordCaptor.getValue();
        assertThat(persisted).isNotEqualTo("Test1234");
        assertThat(persisted).startsWith("$2");
        assertThat(encoder.matches("Test1234", persisted)).isTrue();
    }

    @Test
    @DisplayName("BCrypt 雜湊長度為 60，符合 user.password VARCHAR(72) 設計")
    void hashFitsColumnWidth() {
        when(userRepository.register(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        service().register(command());

        org.mockito.Mockito.verify(userRepository)
                .register(anyString(), anyString(), anyString(), passwordCaptor.capture(), anyString());

        assertThat(passwordCaptor.getValue()).hasSize(60);
    }

    @Test
    @DisplayName("自我介紹為 null 時以空字串寫入（C-4）")
    void nullBiographyBecomesEmptyString() {
        when(userRepository.register(anyString(), anyString(), anyString(), anyString(), anyString()))
                .thenReturn(1L);

        ArgumentCaptor<String> biographyCaptor = ArgumentCaptor.forClass(String.class);
        service().register(
                new RegisterCommand("0912345678", "陳大文", "alice@example.com", "Test1234", null));

        org.mockito.Mockito.verify(userRepository)
                .register(anyString(), anyString(), anyString(), anyString(), biographyCaptor.capture());

        assertThat(biographyCaptor.getValue()).isEmpty();
    }

    @Test
    @DisplayName("手機號碼重複時轉譯為 DuplicatePhoneException")
    void translatesDuplicateKeyToDomainException() {
        when(userRepository.register(anyString(), anyString(), anyString(), anyString(), any()))
                .thenThrow(new DuplicateKeyException("uk_user_phone"));

        assertThatThrownBy(() -> service().register(command()))
                .isInstanceOf(DuplicatePhoneException.class);
    }
}
