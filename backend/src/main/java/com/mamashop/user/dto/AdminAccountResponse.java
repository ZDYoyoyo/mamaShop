package com.mamashop.user.dto;

import com.mamashop.user.entity.User;
import java.time.Instant;

/** 後台管理員帳號列表的一筆（不含密碼雜湊） */
public record AdminAccountResponse(
        Long id, String email, String name, String phone, boolean enabled,
        Instant createdAt, Instant passwordChangedAt) {

    public static AdminAccountResponse from(User user) {
        return new AdminAccountResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(),
                user.isEnabled(), user.getCreatedAt(), user.getPasswordChangedAt());
    }
}
