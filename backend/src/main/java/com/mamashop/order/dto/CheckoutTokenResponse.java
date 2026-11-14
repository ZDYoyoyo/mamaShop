package com.mamashop.order.dto;

/**
 * 結帳用的一次性識別碼（R02）。送出訂單時放在 Idempotency-Key header。
 *
 * @param token            隨機 UUID
 * @param expiresInSeconds 幾秒後失效
 */
public record CheckoutTokenResponse(String token, long expiresInSeconds) {
}
