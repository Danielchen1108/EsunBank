package com.esunbank.social.common.exception;

/**
 * 登入失敗——手機號碼不存在或密碼不符。
 *
 * <p>登入驗證功能。
 *
 * <p><b>兩種失敗原因刻意共用同一個例外：</b>若「查無此手機號碼」與「密碼錯誤」
 * 回不同訊息，任何人都能靠登入端點逐一探測哪些手機號碼已註冊。
 * 需求未定義此行為，此處選擇不區分。
 *
 * <p>本例外不攜帶手機號碼——它是 PII，且錯誤回應中不需要它。
 */
public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException() {
        super("手機號碼或密碼錯誤");
    }
}
