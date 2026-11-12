package com.mamashop.user.dto;

import jakarta.validation.constraints.NotNull;

/** 停用（false）／啟用（true）帳號 */
public record AccountEnabledRequest(@NotNull Boolean enabled) {
}
