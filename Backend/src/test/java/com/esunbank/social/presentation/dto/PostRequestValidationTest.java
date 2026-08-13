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
 * 發文請求的欄位驗證（F004 AC-1、AC-4）。
 *
 * <p>content 上限 2000 字元為 ADR-005 的裁決，與 {@code post.content VARCHAR(2000)} 一致。
 * 在應用層先擋下，可避免超長輸入到資料庫才以 SQLException 失敗——那會回 500 而非可讀的 400。
 *
 * <p>邊界取 2000／2001：只測「遠超過」無法證明上限落在正確位置。
 */
class PostRequestValidationTest {

    private static Validator validator;

    @BeforeAll
    static void setUp() {
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
    }

    private static String repeat(char c, int times) {
        return String.valueOf(c).repeat(times);
    }

    private Set<ConstraintViolation<CreatePostRequest>> violationsOf(CreatePostRequest request) {
        return validator.validate(request);
    }

    private Set<ConstraintViolation<UpdatePostRequest>> violationsOf(UpdatePostRequest request) {
        return validator.validate(request);
    }

    @Test
    @DisplayName("新增發文：內容 2000 字元時通過（ADR-005 上限）")
    void acceptsContentAtMaxLength() {
        assertThat(violationsOf(new CreatePostRequest(repeat('a', 2000), null))).isEmpty();
    }

    @Test
    @DisplayName("新增發文：內容 2001 字元時被拒")
    void rejectsContentOverMaxLength() {
        assertThat(violationsOf(new CreatePostRequest(repeat('a', 2001), null)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("新增發文：內容空白時被拒——發文必須有內容")
    void rejectsBlankContent() {
        assertThat(violationsOf(new CreatePostRequest("   ", null)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("新增發文：圖片路徑未帶時通過——題目標示 Image 為非必要欄位")
    void allowsNullImage() {
        assertThat(violationsOf(new CreatePostRequest("今天天氣真好", null))).isEmpty();
    }

    @Test
    @DisplayName("新增發文：圖片路徑超過 255 時被拒——對應 post.image VARCHAR(255)")
    void rejectsImagePathOverColumnWidth() {
        assertThat(violationsOf(new CreatePostRequest("內容", repeat('p', 256))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("image");
    }

    @Test
    @DisplayName("編輯發文：內容 2000 字元時通過，2001 字元被拒——與新增同規則")
    void updateAppliesSameContentLimit() {
        assertThat(violationsOf(new UpdatePostRequest(repeat('a', 2000), null))).isEmpty();
        assertThat(violationsOf(new UpdatePostRequest(repeat('a', 2001), null)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("編輯發文：內容空白時被拒")
    void updateRejectsBlankContent() {
        assertThat(violationsOf(new UpdatePostRequest("", null)))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }
}
