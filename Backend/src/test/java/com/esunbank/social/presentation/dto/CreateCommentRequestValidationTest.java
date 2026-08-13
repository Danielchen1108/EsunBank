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
 * 新增留言請求的欄位驗證（F005 AC-1）。
 *
 * <p>500 字上限來自 ADR-005（owner 裁決），對應 schema 的
 * {@code comment.content VARCHAR(500)}。在應用層先驗證的理由：
 * 若放任超長內容送到資料庫，MySQL 會以 {@code Data too long} 中斷，
 * 使用者收到的是資料庫錯誤而非可讀的欄位提示。
 *
 * <p><b>刻意不驗證</b>的項目：留言內容不做 HTML 過濾或跳脫——
 * XSS 的防護位置在輸出端（見 {@code F005-API.md} § 安全考量）。
 */
class CreateCommentRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private Set<ConstraintViolation<CreateCommentRequest>> violationsOf(String content) {
        return validator.validate(new CreateCommentRequest(content));
    }

    @Test
    @DisplayName("一般留言內容通過")
    void acceptsOrdinaryContent() {
        assertThat(violationsOf("歡迎加入！👋")).isEmpty();
    }

    @Test
    @DisplayName("恰好 500 字通過——上限為包含（ADR-005）")
    void acceptsExactlyFiveHundredCharacters() {
        assertThat(violationsOf("字".repeat(500))).isEmpty();
    }

    @Test
    @DisplayName("501 字被拒——超過 comment.content VARCHAR(500)")
    void rejectsFiveHundredAndOneCharacters() {
        assertThat(violationsOf("字".repeat(501)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("留言內容為空字串時被拒")
    void rejectsEmptyContent() {
        assertThat(violationsOf(""))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("留言內容僅有空白時被拒——空白留言無意義")
    void rejectsBlankContent() {
        assertThat(violationsOf("   "))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("留言內容為 null 時被拒")
    void rejectsNullContent() {
        assertThat(violationsOf(null))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }
}
