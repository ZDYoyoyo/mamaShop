package com.mamashop.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 新增管理員（例如幫另一位家人開帳號）。
 * 建立後請對方登入，再用「重設密碼」改成自己的密碼。
 */
public record CreateAdminRequest(
        @NotBlank @Email @Size(max = 255) String email,
        @NotBlank @ValidPassword String password,
        @NotBlank @Size(max = 50) String name,
        @Pattern(regexp = "^09\\d{8}$", message = "需為 09 開頭的 10 碼手機號碼") String phone) {

    public CreateAdminRequest {
        email = email == null ? null : email.trim();
        name = name == null ? null : name.trim();
        phone = phone == null || phone.isBlank() ? null : phone.trim();
    }
}
