package com.esunbank.social.business.service;

/**
 * 編輯發文的業務指令（業務層）。
 *
 * <p><b>刻意不含操作者 ID。</b>需求 §2 原文為「確保只有登入的使用者可以發文或留言」，
 * 字面上未涵蓋編輯與刪除，owner 於 {@code F004-REQ.md} § BG-4 裁決不實作發文者身分檢查。
 * 指令不帶操作者身分，使「任何登入者皆可編輯」成為結構上的事實，
 * 而非某處忘了比對——避免後續 review 誤判為授權邏輯遺漏。
 *
 * <p><b>不含 image：</b>API 不開放填寫圖片路徑（無上傳功能，`SCOPE-BOUNDARY.md` R-3）；
 * {@code sp_post_update} 的 image 參數由資料層固定傳 null。
 *
 * @param postId  目標發文
 * @param content 新的發文內容
 */
public record PostUpdateCommand(Long postId, String content) {
}
