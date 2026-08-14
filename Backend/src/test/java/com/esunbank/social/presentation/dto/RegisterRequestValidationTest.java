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
 * 註冊請求的欄位驗證（F002 AC-8）。
 *
 * <p>手機號碼長度為 owner 於 2026-08-13 追加的要求：**僅驗長度 10 碼**，
 * 不驗開頭數字與國別碼（見 SCOPE-BOUNDARY.md）。
 */
class RegisterRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private RegisterRequest requestWithPhone(String phone) {
        return new RegisterRequest(phone, "陳大文", "alice@example.com", "Test1234", "自我介紹");
    }

    private Set<ConstraintViolation<RegisterRequest>> violationsOf(RegisterRequest request) {
        return validator.validate(request);
    }

    @Test
    @DisplayName("手機號碼恰好 10 碼時通過")
    void acceptsExactlyTenDigits() {
        assertThat(violationsOf(requestWithPhone("0912345678"))).isEmpty();
    }

    @Test
    @DisplayName("手機號碼 9 碼時被拒")
    void rejectsNineDigits() {
        assertThat(violationsOf(requestWithPhone("091234567")))
                .extracting(ConstraintViolation::getPropertyPath)
                .anyMatch(path -> path.toString().equals("phone"));
    }

    @Test
    @DisplayName("手機號碼 11 碼時被拒")
    void rejectsElevenDigits() {
        assertThat(violationsOf(requestWithPhone("09123456789")))
                .extracting(ConstraintViolation::getPropertyPath)
                .anyMatch(path -> path.toString().equals("phone"));
    }

    @Test
    @DisplayName("不驗證開頭數字——需求未要求，非 09 開頭的 10 碼仍通過")
    void doesNotValidatePrefix() {
        assertThat(violationsOf(requestWithPhone("1234567890"))).isEmpty();
    }

    @Test
    @DisplayName("必填欄位為空白時被拒")
    void rejectsBlankRequiredFields() {
        RegisterRequest request = new RegisterRequest("0912345678", "  ", "", "", null);

        assertThat(violationsOf(request))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("userName", "email", "password");
    }

    @Test
    @DisplayName("自我介紹未填時通過——需求標示可為空（C-4）")
    void allowsNullBiography() {
        RegisterRequest request =
                new RegisterRequest("0912345678", "陳大文", "alice@example.com", "Test1234", null);

        assertThat(violationsOf(request)).isEmpty();
    }
}
