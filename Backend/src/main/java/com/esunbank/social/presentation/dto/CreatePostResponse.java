package com.esunbank.social.presentation.dto;

/**
 * 新增發文成功回應。
 *
 * <p>僅回傳 postId，與註冊回應（{@code RegisterResponse}）一致：
 * 建立成功後前端如需完整資料，可再取列表——回傳最小必要資訊即可。
 */
public record CreatePostResponse(Long postId) {
}
