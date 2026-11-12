package com.mamashop.user.dto;

import jakarta.validation.constraints.NotBlank;

/** 重設密碼 */
public record ResetPasswordRequest(@NotBlank @ValidPassword String newPassword) {
}
