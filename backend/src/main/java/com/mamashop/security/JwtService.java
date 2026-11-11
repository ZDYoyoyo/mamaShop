package com.mamashop.security;

import com.mamashop.user.entity.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * 產生與驗證 JWT。
 *
 * <p>token 只放會員 id（sub）與時間，不放 email、角色等資料：
 * JWT 只是 Base64 編碼，任何人都能解開來看內容；角色則每次請求從資料庫讀最新的
 * （見 {@link JwtAuthenticationFilter}），帳號被停用或降級時馬上生效。
 */
@Service
public class JwtService {

    /** HS256 要求密鑰至少 256 bits = 32 bytes */
    private static final int MIN_SECRET_BYTES = 32;

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        String secret = properties.secret();
        if (secret == null || secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            // 啟動時就失敗（fail fast），不要等到有人登入才發現
            throw new IllegalStateException("JWT_SECRET 未設定或長度不足 32 字元");
        }
        if (secret.startsWith("change-me")) {
            throw new IllegalStateException("JWT_SECRET 仍是 .env.example 的範例值，請換成自己產生的隨機字串");
        }
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    public String generateToken(User user) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key)
                .compact();
    }

    /**
     * 驗證簽章與有效期限，成功回傳會員 id。
     * 簽章不符（被竄改）、過期、格式錯誤都回傳 empty，不丟例外：交給權限設定決定要不要擋。
     */
    public Optional<Long> parseUserId(String token) {
        try {
            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(Long.valueOf(claims.getSubject()));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }

    public Duration getExpiration() {
        return expiration;
    }
}
