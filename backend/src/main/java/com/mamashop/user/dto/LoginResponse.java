package com.mamashop.user.dto;

/**
 * @param accessToken 之後的請求帶在 header：Authorization: Bearer {accessToken}
 * @param expiresIn   有效秒數
 * @param user        登入者資料，前端用來顯示名字、判斷要不要顯示後台入口
 */
public record LoginResponse(String accessToken, String tokenType, long expiresIn, UserResponse user) {
}
