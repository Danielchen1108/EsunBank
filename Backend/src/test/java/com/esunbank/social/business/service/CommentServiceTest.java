package com.esunbank.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.esunbank.social.common.exception.CommentTargetPostNotFoundException;
import com.esunbank.social.data.repository.CommentRepository;

/**
 * 新增留言業務邏輯（F005 AC-1、AC-3）。
 *
 * <p>本測試的核心是證明：**留言的作者取自已驗證的身分，不取自請求內容**。
 * 需求 §2 要求「確保只有登入的使用者可以發文或留言」——若 userId 可由
 * 請求主體指定，任何人都能冒用他人身分留言。
 *
 * <p>不依賴真實資料庫：資料層以 mock 取代（見 {@code F005-TR.md} 測試分層）。
 */
@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Captor
    private ArgumentCaptor<Long> userIdCaptor;

    @Captor
    private ArgumentCaptor<Long> postIdCaptor;

    @Captor
    private ArgumentCaptor<String> contentCaptor;

    private CommentService service() {
        return new CommentService(commentRepository);
    }

    private CommentCreateCommand command() {
        return new CommentCreateCommand(7L, 3L, "照片拍得不錯");
    }

    @Test
    @DisplayName("回傳資料層產生的 comment_id")
    void returnsGeneratedCommentId() {
        when(commentRepository.create(anyLong(), anyLong(), anyString())).thenReturn(42L);

        assertThat(service().create(command())).isEqualTo(42L);
    }

    @Test
    @DisplayName("使用者、發文、內容原樣傳給資料層——userId 與 postId 不得互換")
    void passesAllFieldsToDataLayer() {
        when(commentRepository.create(anyLong(), anyLong(), anyString())).thenReturn(42L);

        service().create(command());

        verify(commentRepository)
                .create(userIdCaptor.capture(), postIdCaptor.capture(), contentCaptor.capture());

        assertThat(userIdCaptor.getValue()).isEqualTo(7L);
        assertThat(postIdCaptor.getValue()).isEqualTo(3L);
        assertThat(contentCaptor.getValue()).isEqualTo("照片拍得不錯");
    }

    @Test
    @DisplayName("留言內容不被改寫——XSS 防護在輸出端，寫入端不得竄改使用者資料")
    void doesNotRewriteContent() {
        when(commentRepository.create(anyLong(), anyLong(), anyString())).thenReturn(42L);

        String hostile = "<script>alert(1)</script>";
        service().create(new CommentCreateCommand(7L, 3L, hostile));

        verify(commentRepository).create(anyLong(), anyLong(), contentCaptor.capture());
        assertThat(contentCaptor.getValue()).isEqualTo(hostile);
    }

    @Test
    @DisplayName("目標發文不存在或已軟刪除時，領域例外原樣往上傳遞")
    void propagatesTargetPostNotFound() {
        when(commentRepository.create(anyLong(), anyLong(), anyString()))
                .thenThrow(new CommentTargetPostNotFoundException(3L));

        assertThatThrownBy(() -> service().create(command()))
                .isInstanceOf(CommentTargetPostNotFoundException.class);
    }
}
