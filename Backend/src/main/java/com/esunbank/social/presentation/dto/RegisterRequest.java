package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 註冊請求。
 *
 * <p>驗證規則僅涵蓋需求明文要求與 後來追加項目。
 * <b>刻意不驗證</b>手機開頭數字、國別碼、Email 格式、密碼強度——
 * 需求均未定義，依 判定原則 R-3 不實作。
 *
 * @param phone     手機號碼，註冊與登入帳號。長度恰好 10 碼
 * @param userName  使用者名稱
 * @param email     電子郵件
 * @param password  明碼密碼。業務層會以 BCrypt 加鹽雜湊後才交給資料層
 * @param biography 自我介紹，可不填；未填時以空字串寫入
 */
public record RegisterRequest(

        @NotBlank(message = "手機號碼為必填")
        @Size(min = 10, max = 10, message = "手機號碼必須為 10 碼")
        String phone,

        @NotBlank(message = "使用者名稱為必填")
        @Size(max = 50, message = "使用者名稱不可超過 50 字")
        String userName,

        @NotBlank(message = "電子郵件為必填")
        @Size(max = 255, message = "電子郵件不可超過 255 字")
        String email,

        @NotBlank(message = "密碼為必填")
        String password,

        @Size(max = 500, message = "自我介紹不可超過 500 字")
        String biography) {
}
