package com.esunbank.social.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

/**
 * 登入請求的欄位驗證。
 *
 * <p><b>刻意只驗必填</b>：登入不重複註冊的格式規則。手機號碼長度不符者必然查無此帳號，
 * 由既有的登入失敗路徑處理即可；在此另設長度規則只會讓「格式錯誤」與「帳密錯誤」
 * 回不同狀態碼，反而洩漏哪些輸入曾是合法帳號。
 */
class LoginRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private Set<ConstraintViolation<LoginRequest>> violationsOf(LoginRequest request) {
        return validator.validate(request);
    }

    @Test
    @DisplayName("手機號碼與密碼齊備時通過")
    void acceptsCompleteRequest() {
        assertThat(violationsOf(new LoginRequest("0912345678", "Test1234"))).isEmpty();
    }

    @Test
    @DisplayName("手機號碼空白時被拒")
    void rejectsBlankPhone() {
        assertThat(violationsOf(new LoginRequest("  ", "Test1234")))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("phone");
    }

    @Test
    @DisplayName("密碼空白時被拒")
    void rejectsBlankPassword() {
        assertThat(violationsOf(new LoginRequest("0912345678", "")))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("password");
    }

    @Test
    @DisplayName("不驗證密碼強度——需求未定義")
    void doesNotValidatePasswordStrength() {
        assertThat(violationsOf(new LoginRequest("0912345678", "a"))).isEmpty();
    }

    @Test
    @DisplayName("手機號碼超過 10 碼被拒——user.phone 為 CHAR(10)，更長者傳入 SP 會截斷報錯")
    void rejectsPhoneLongerThanColumn() {
        assertThat(violationsOf(new LoginRequest("0912345678901234567890", "Test1234")))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("phone");
    }

    @Test
    @DisplayName("手機號碼短於 10 碼仍通過——交由登入失敗路徑統一回 401，不因長度給出不同回應")
    void allowsShorterPhoneAndLetsLoginFail() {
        assertThat(violationsOf(new LoginRequest("091234567", "Test1234"))).isEmpty();
    }
}
