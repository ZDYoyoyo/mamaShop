package com.mamashop.user.dto;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;

/** 會員資料（不含密碼雜湊）。 */
public record UserResponse(Long id, String email, String name, String phone, Role role) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(), user.getName(), user.getPhone(), user.getRole());
    }
}
