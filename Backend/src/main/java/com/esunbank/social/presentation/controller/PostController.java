package com.esunbank.social.presentation.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.esunbank.social.business.service.Post;
import com.esunbank.social.business.service.PostCreateCommand;
import com.esunbank.social.business.service.PostService;
import com.esunbank.social.business.service.PostUpdateCommand;
import com.esunbank.social.common.security.AuthenticatedUser;
import com.esunbank.social.presentation.dto.CreatePostRequest;
import com.esunbank.social.presentation.dto.CreatePostResponse;
import com.esunbank.social.presentation.dto.PostResponse;
import com.esunbank.social.presentation.dto.UpdatePostRequest;

import jakarta.validation.Valid;

/**
 * 發文端點（展示層）。
 *
 * <p>對應需求 §3「新增發文／列出所有發文／編輯或刪除發文」。
 *
 * <p><b>RESTful 設計（需求 §6）：</b>以 {@code /api/posts} 為資源集合，
 * 動作由 HTTP 方法表達（POST 新增、GET 讀取、PUT 取代、DELETE 刪除），
 * 路徑中不出現動詞。
 *
 * <p><b>全部端點需登入：</b>{@code SecurityConfig} 為 deny-by-default，
 * F003 負責將 {@code /api/posts/**} 納入需驗證範圍並於過濾鏈填入
 * {@link AuthenticatedUser} principal。本控制器不自行檢查憑證——那是共用層的職責。
 *
 * <p><b>編輯與刪除刻意不做發文者身分檢查（{@code F004-REQ.md} § BG-4 裁決）：</b>
 * 需求 §2 原文「確保只有登入的使用者可以<b>發文或留言</b>」，字面上未涵蓋編輯與刪除。
 * owner 依「沒寫就不用」明示裁決不實作，並接受「任何登入使用者可編輯或刪除他人發文」的風險。
 * 因此 {@link #update} 與 {@link #delete} 不取用目前登入者——<b>這是刻意決策，不是遺漏</b>。
 */
@RestController
@RequestMapping("/api/posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    /**
     * 新增發文（AC-1、AC-6）。
     *
     * <p>回 201 Created 而非 200——建立了新資源。
     *
     * <p>發文者取自 {@code currentUser} 而非請求內容：若由請求指定 userId，
     * 使用者就能冒用他人身分發文，需求 §2 的驗證要求形同虛設。
     */
    @PostMapping
    public ResponseEntity<CreatePostResponse> create(
            @AuthenticationPrincipal AuthenticatedUser currentUser,
            @Valid @RequestBody CreatePostRequest request) {

        if (currentUser == null) {
            // 正常情況不可達：SecurityConfig 已要求本端點需驗證（F003 負責）。
            // 保留明確的 401 而非讓 NPE 變成 500——過濾鏈若設定錯誤，回應語意仍須正確。
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long postId = postService.create(new PostCreateCommand(
                currentUser.userId(),
                request.content()));

        return ResponseEntity.status(HttpStatus.CREATED).body(new CreatePostResponse(postId));
    }

    /**
     * 列出所有發文（AC-3）。
     *
     * <p>已軟刪除的發文不會出現——過濾條件在 {@code sp_post_list} 內（ADR-004）。
     *
     * <p><b>每則發文一併帶出其留言</b>（OQ-1 原決定不帶出，後由 owner 明示追加，D-13）——
     * 一次請求就取得畫面所需的全部資料，前端不必逐篇再查留言（N+1）。
     * 沒有留言的發文回傳空陣列。
     *
     * <p>留言依時間排序（順序即對話），但<b>發文本身不排序、不分頁</b>——
     * 需求未定義發文的排序規則，依 `SCOPE-BOUNDARY.md` 判定原則 R-3 不實作。
     */
    @GetMapping
    public ResponseEntity<List<PostResponse>> list() {
        List<PostResponse> posts = postService.listAll().stream()
                .map(PostResponse::from)
                .toList();

        return ResponseEntity.ok(posts);
    }

    /**
     * 編輯發文（AC-4）。
     *
     * <p>PUT 而非 PATCH：請求提供發文可修改欄位（content）的完整新值，為整體取代語意。
     *
     * <p>不檢查操作者是否為發文者——見類別說明的 BG-4 裁決。
     */
    @PutMapping("/{postId}")
    public ResponseEntity<PostResponse> update(
            @PathVariable Long postId,
            @Valid @RequestBody UpdatePostRequest request) {

        Post updated = postService.update(new PostUpdateCommand(
                postId,
                request.content()));

        return ResponseEntity.ok(PostResponse.from(updated));
    }

    /**
     * 刪除發文（AC-5、AC-11）。
     *
     * <p>軟刪除，並於同一交易內連動軟刪除其留言（ADR-004）——
     * 需求 §6「需同時異動多個資料表時，請實作 Transaction」的落地點。
     *
     * <p>回 204 No Content：刪除成功且無內容可回。
     *
     * <p>不檢查操作者是否為發文者——見類別說明的 BG-4 裁決。
     */
    @DeleteMapping("/{postId}")
    public ResponseEntity<Void> delete(@PathVariable Long postId) {
        postService.delete(postId);

        return ResponseEntity.noContent().build();
    }
}
