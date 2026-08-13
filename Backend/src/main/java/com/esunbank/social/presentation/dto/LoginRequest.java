package com.esunbank.social.presentation.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 登入請求（題目 §1、§2）。
 *
 * <p><b>不驗最小長度</b>——與 {@code RegisterRequest} 不同。註冊要決定什麼資料能進資料庫，
 * 登入只要判斷帳密對不對。10 碼以下的手機號碼必然查無此帳號，由登入失敗路徑統一回 401 即可；
 * 若在此另設下限，格式錯誤（400）與帳密錯誤（401）就會回不同狀態碼，
 * 反而透露哪些輸入曾是合法帳號的形狀。
 *
 * <p><b>但驗最大長度 10</b>——這不是格式規則，是資料型別的邊界：
 * {@code user.phone} 為 {@code CHAR(10)}，{@code sp_user_find_by_phone} 的參數同樣是
 * {@code CHAR(10)}。超過 10 碼的輸入在資料庫端會因截斷而拋錯（strict mode），
 * 使原本該是 401 的請求變成伺服器錯誤。於邊界擋下，比讓資料庫報錯更明確。
 *
 * @param phone    手機號碼，即登入帳號
 * @param password 明碼密碼。業務層以 BCrypt 比對雜湊值，不會被儲存或記錄
 */
public record LoginRequest(

        @NotBlank(message = "手機號碼為必填")
        @Size(max = 10, message = "手機號碼不可超過 10 碼")
        String phone,

        @NotBlank(message = "密碼為必填")
        String password) {
}
