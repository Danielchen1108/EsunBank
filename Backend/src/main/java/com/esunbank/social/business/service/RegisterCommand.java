package com.esunbank.social.business.service;

/**
 * 註冊指令（業務層輸入）。
 *
 * <p>與 {@code RegisterRequest} 分離的理由：業務層不得依賴展示層型別
 * （見 {@code business/package-info.java} 的依賴方向限制）。
 * 展示層負責把 HTTP 請求轉為本型別。
 *
 * @param password 明碼密碼。業務層負責雜湊，明碼不得流入資料層
 */
public record RegisterCommand(
        String phone,
        String userName,
        String email,
        String password,
        String biography) {
}
