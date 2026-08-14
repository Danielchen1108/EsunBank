package com.esunbank.social.presentation.dto;

/**
 * 新增留言成功回應。
 *
 * <p>僅回傳 commentId。不回傳留言全文或作者資料——
 * 用戶端剛送出這些內容，回傳一次沒有新資訊。
 *
 * <p>也不回傳該發文的更新後留言列表：留言隨 {@code GET /api/posts} 一併帶出，
 * 前端新增成功後重新載入列表即可取得，在這裡再回一份只是同一份資料的第二個來源，
 * 兩邊格式一旦分歧就會出現「送出後看到的」與「重整後看到的」不一致。
 */
public record CreateCommentResponse(Long commentId) {
}
