package com.esunbank.social.business.service;

/**
 * 登入指令（業務層輸入）。
 *
 * <p>與 {@code LoginRequest} 分離的理由：業務層不得依賴展示層型別
 * （見 {@code business/package-info.java} 的依賴方向限制）。
 * 展示層負責把 HTTP 請求轉為本型別。
 *
 * @param phone    手機號碼，即登入帳號
 * @param password 明碼密碼。僅用於與資料庫中的雜湊值比對，不會被儲存或記錄
 */
public record LoginCommand(String phone, String password) {
}
