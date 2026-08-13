package com.esunbank.social.presentation.dto;

/**
 * 新增留言成功回應。
 *
 * <p>僅回傳 commentId。不回傳留言全文或作者資料——
 * 用戶端剛送出這些內容，回傳一次沒有新資訊；
 * 也不回傳該發文的留言列表，列出留言為 Out of Scope
 * （{@code SCOPE-BOUNDARY.md}，題目 §4 僅要求新增）。
 */
public record CreateCommentResponse(Long commentId) {
}
