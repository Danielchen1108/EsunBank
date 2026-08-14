package com.esunbank.social.presentation.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.esunbank.social.business.service.CommentCreateCommand;
import com.esunbank.social.business.service.CommentService;
import com.esunbank.social.common.security.AuthenticatedUser;
import com.esunbank.social.presentation.dto.CreateCommentRequest;
import com.esunbank.social.presentation.dto.CreateCommentResponse;

import jakarta.validation.Valid;

/**
 * 留言端點（展示層）。
 *
 * <p>需求：使用者可以針對發文新增留言。
 *
 * <p><b>本控制器只有新增。</b>需求只有這一句；編輯與刪除留言依
 * 不做。
 *
 * <p><b>讀取留言走 {@code GET /api/posts}：</b>發文列表已一併帶出每則發文的留言，
 * 本控制器因此不需要、也刻意不提供留言的讀取端點——前端要的是「每則發文底下的留言」，
 * 另開一支端點只會讓它逐篇再查一次（N+1）。讀取邏輯因此歸發文那一側，
 * 本控制器只負責新增。
 *
 * <p><b>需驗證：</b>本控制器的端點<b>不在</b> {@code SecurityConfig} 白名單內，
 * 由 {@code anyRequest().authenticated()} 的 deny-by-default 涵蓋，
 * 因此 無須為本功能追加規則。未帶有效憑證時在過濾鏈即被擋為 401，
 * 請求不會進到本類別。
 */
@RestController
@RequestMapping("/api/posts")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    /**
     * 針對指定發文新增留言。
     *
     * <p>路徑設計為 {@code /api/posts/{postId}/comments}：留言是發文的附屬資源，
     * 巢狀路徑使歸屬關係直接呈現在 URI 上，符合需求的 RESTful 要求。
     *
     * <p>回 201 Created 而非 200——建立了新資源。
     *
     * <p>{@code currentUser} 由驗證過濾鏈放入 {@code SecurityContext}
     * （見 {@link AuthenticatedUser}）。留言者只取自此處，<b>不接受請求主體指定</b>。
     */
    @PostMapping("/{postId}/comments")
    public ResponseEntity<CreateCommentResponse> create(
            @PathVariable Long postId,
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreateCommentRequest request) {

        Long commentId = commentService.create(new CommentCreateCommand(
                currentUser.userId(),
                postId,
                request.content()));

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new CreateCommentResponse(commentId));
    }
}
