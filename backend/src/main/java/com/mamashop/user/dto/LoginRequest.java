package com.mamashop.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record LoginRequest(
        @NotBlank @Size(max = 255) String email,
        @NotBlank @Size(max = 72) String password) {

    /** 去除 Email 前後空白（密碼不處理） */
    public LoginRequest {
        email = email == null ? null : email.trim();
    }
}
