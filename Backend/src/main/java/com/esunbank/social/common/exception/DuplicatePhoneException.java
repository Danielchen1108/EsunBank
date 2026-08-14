package com.esunbank.social.common.exception;

/**
 * 手機號碼已被註冊。
 *
 * <p>由業務層自 {@code DuplicateKeyException} 轉譯而來——資料庫的
 * {@code uk_user_phone} 唯一約束是此情境的最終判定者
 * （見 {@code F001-DB.md} OQ-1）。
 *
 * <p>需求未定義重複註冊的行為（{@code F002-REQ.md} OQ-1），
 * 但 UNIQUE 約束使其必然發生，故須明確處理而非讓例外直接外洩。
 */
public class DuplicatePhoneException extends RuntimeException {

    private final String phone;

    public DuplicatePhoneException(String phone) {
        super("手機號碼已被註冊：" + phone);
        this.phone = phone;
    }

    public String getPhone() {
        return phone;
    }
}
