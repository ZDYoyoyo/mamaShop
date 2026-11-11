package com.mamashop.security;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 對應 application.yml 的 app.jwt.*。
 * 用 @ConfigurationProperties 把一組設定綁成一個物件，取代到處寫 @Value("${...}")。
 */
@ConfigurationProperties("app.jwt")
public record JwtProperties(String secret, Duration expiration) {
}
