package com.esunbank.social.presentation.dto;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.RecordComponent;
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
        assertThat(violationsOf(new CreatePostRequest(repeat('a', 2000)))).isEmpty();
    }

    @Test
    @DisplayName("新增發文：內容 2001 字元時被拒")
    void rejectsContentOverMaxLength() {
        assertThat(violationsOf(new CreatePostRequest(repeat('a', 2001))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("新增發文：內容空白時被拒——發文必須有內容")
    void rejectsBlankContent() {
        assertThat(violationsOf(new CreatePostRequest("   ")))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("新增與編輯發文的請求皆不含 image——欄位保留於 schema，但 API 不開放填寫")
    void requestsDoNotExposeImage() {
        // 需求規格列有 Post.Image（標「非必要欄位」），故 DB schema 保留該欄；
        // 但需求功能清單 §1–§4 沒有上傳功能，依 SCOPE-BOUNDARY.md R-3「沒寫就不用」，
        // API 不開放填寫。此處把該裁決固化為可執行的規格，避免日後被當成漏掉的欄位補回來。
        assertThat(CreatePostRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("image");

        assertThat(UpdatePostRequest.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("image");

        assertThat(PostResponse.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("image");
    }

    @Test
    @DisplayName("編輯發文：內容 2000 字元時通過，2001 字元被拒——與新增同規則")
    void updateAppliesSameContentLimit() {
        assertThat(violationsOf(new UpdatePostRequest(repeat('a', 2000)))).isEmpty();
        assertThat(violationsOf(new UpdatePostRequest(repeat('a', 2001))))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }

    @Test
    @DisplayName("編輯發文：內容空白時被拒")
    void updateRejectsBlankContent() {
        assertThat(violationsOf(new UpdatePostRequest("")))
                .extracting(v -> v.getPropertyPath().toString())
                .contains("content");
    }
}
