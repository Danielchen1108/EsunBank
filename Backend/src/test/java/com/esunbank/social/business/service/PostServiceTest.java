package com.esunbank.social.business.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.esunbank.social.data.repository.PostRepository;
import com.esunbank.social.data.repository.PostRepository.PostRow;

/**
 * 發文業務邏輯（F004 AC-1、AC-3、AC-4、AC-5、AC-11）。
 *
 * <p>以 mock 取代資料層，本測試不連資料庫——SP 的實際行為由
 * {@code PostRepositoryIntegrationTest} 驗證。
 *
 * <p><b>本測試刻意涵蓋的兩個設計決策：</b>
 * <ol>
 *   <li>編輯前必須先確認目標存在且未被軟刪除（ADR-004 負面後果防範）</li>
 *   <li>編輯／刪除不檢查發文者身分（BG-4 裁決）——業務層方法簽章不接受操作者 ID，
 *       即為「不做身分檢查」的結構性證明</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    private PostRepository postRepository;

    @InjectMocks
    private PostService postService;

    /**
     * 資料層回傳的資料列。
     *
     * <p>回傳 {@link PostRow} 而非領域模型 {@link Post}：資料層不得依賴業務層
     * （{@code data/package-info.java}），故 repository 回傳自己的資料列型別，
     * 由 {@code PostService} 轉為領域模型。本測試驗證的正是這段映射。
     */
    private PostRow post(long postId, String content) {
        return new PostRow(postId, 1L, "陳大文", content, LocalDateTime.of(2026, 8, 13, 10, 0));
    }

    @Test
    @DisplayName("新增發文：以目前登入者的 userId 寫入")
    void createsPostWithCurrentUserId() {
        when(postRepository.create(eq(2L), eq("今天天氣真好"))).thenReturn(9L);

        Long postId = postService.create(new PostCreateCommand(2L, "今天天氣真好"));

        assertThat(postId).isEqualTo(9L);
        verify(postRepository).create(2L, "今天天氣真好");
    }

    @Test
    @DisplayName("列出所有發文：原樣回傳資料層結果——軟刪除過濾由 sp_post_list 負責")
    void listsAllPosts() {
        when(postRepository.findAll()).thenReturn(List.of(post(1L, "第一篇"), post(2L, "第二篇")));

        assertThat(postService.listAll())
                .extracting(Post::content)
                .containsExactly("第一篇", "第二篇");
    }

    @Test
    @DisplayName("編輯發文：先確認目標存在，再呼叫更新")
    void updatesExistingPost() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post(1L, "原始內容")));
        when(postRepository.update(1L, "修改後內容")).thenReturn(1);

        Post updated = postService.update(new PostUpdateCommand(1L, "修改後內容"));

        verify(postRepository).update(1L, "修改後內容");
        assertThat(updated.content()).isEqualTo("修改後內容");
        assertThat(updated.postId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("編輯已軟刪除或不存在的發文：拋 PostNotFoundException 且不呼叫更新")
    void rejectsUpdateOfMissingPost() {
        when(postRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> postService.update(new PostUpdateCommand(99L, "內容")))
                .isInstanceOf(PostNotFoundException.class);

        verify(postRepository, never()).update(anyLong(), any());
    }

    @Test
    @DisplayName("編輯發文：內容與原文相同時仍視為成功——MySQL ROW_COUNT() 為 0 不代表找不到")
    void treatsUnchangedContentAsSuccess() {
        when(postRepository.findById(1L)).thenReturn(Optional.of(post(1L, "相同內容")));
        // MySQL 預設不帶 CLIENT_FOUND_ROWS，UPDATE 未實際改變值時 ROW_COUNT() 回 0。
        // 若以此判定「找不到」，重送相同內容的編輯會誤回 404。
        when(postRepository.update(1L, "相同內容")).thenReturn(0);

        assertThat(postService.update(new PostUpdateCommand(1L, "相同內容")).content())
                .isEqualTo("相同內容");
    }

    @Test
    @DisplayName("刪除發文：呼叫連動軟刪除的 SP")
    void deletesPost() {
        when(postRepository.softDelete(1L)).thenReturn(1);

        postService.delete(1L);

        verify(postRepository).softDelete(1L);
    }

    @Test
    @DisplayName("刪除不存在或已刪除的發文：拋 PostNotFoundException")
    void rejectsDeleteOfMissingPost() {
        when(postRepository.softDelete(99L)).thenReturn(0);

        assertThatThrownBy(() -> postService.delete(99L))
                .isInstanceOf(PostNotFoundException.class);
    }

    @Test
    @DisplayName("編輯與刪除不接受操作者身分——BG-4 裁決：不實作發文者身分檢查")
    void editAndDeleteDoNotTakeAnActorIdentity() {
        // 題目 §2 的驗證範圍僅涵蓋「發文或留言」，未涵蓋編輯與刪除；
        // owner 於 F004-REQ.md BG-4 裁決不實作發文者身分檢查。
        // 編輯指令不含操作者 ID，使「任何登入者皆可編輯／刪除」成為結構性事實而非遺漏。
        assertThat(PostUpdateCommand.class.getRecordComponents())
                .extracting(RecordComponent::getName)
                .doesNotContain("userId");

        when(postRepository.findById(1L)).thenReturn(Optional.of(post(1L, "他人的發文")));
        when(postRepository.update(1L, "被其他登入者改掉")).thenReturn(1);

        // post 的 userId 為 1，操作者身分完全不參與判斷
        assertThat(postService.update(new PostUpdateCommand(1L, "被其他登入者改掉")).content())
                .isEqualTo("被其他登入者改掉");
    }
}
