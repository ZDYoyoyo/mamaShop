package com.mamashop.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 註冊。密碼規則見 {@link ValidPassword}。 */
public record RegisterRequest(
        @NotBlank @Email @Size(max = 255)
        String email,

        @NotBlank @ValidPassword
        String password,

        @NotBlank @Size(max = 50)
        String name,

        // 選填；有填就要是台灣手機格式
        @Pattern(regexp = "^09\\d{8}$", message = "需為 09 開頭的 10 碼手機號碼")
        String phone) {

    /**
     * Compact constructor：JSON 轉成物件時先去除前後空白，之後才做驗證。
     * 顧客從別處複製 Email 常會多帶空白，不該因此被判定格式錯誤。密碼不處理（空白可能是密碼的一部分）。
     */
    public RegisterRequest {
        email = trim(email);
        name = trim(name);
        phone = trim(phone);
    }

    private static String trim(String value) {
        return value == null ? null : value.trim();
    }
}
