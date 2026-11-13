package com.mamashop.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 通知的設定（application.yml 的 app.notification）。
 *
 * @param enabled  是否發送通知（測試預設關閉，避免每個測試都送訊息到 RabbitMQ）
 * @param siteUrl  網站網址，信裡「查看訂單」的連結用
 * @param mailFrom 寄件人，例如 mamaShop &lt;no-reply@example.com&gt;
 */
@ConfigurationProperties(prefix = "app.notification")
public record NotificationProperties(boolean enabled, String siteUrl, String mailFrom) {
}
