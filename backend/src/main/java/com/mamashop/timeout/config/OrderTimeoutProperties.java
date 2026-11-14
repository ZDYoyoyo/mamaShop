package com.mamashop.timeout.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 逾時自動處理的設定（M02），對應 application.yml 的 app.order-timeout。
 *
 * @param enabled              關閉時不排定任何延遲任務（測試預設關閉）
 * @param autoCompleteAfter    宅配訂單「已送達」之後多久自動完成，例如 7d
 */
@ConfigurationProperties(prefix = "app.order-timeout")
public record OrderTimeoutProperties(boolean enabled, Duration autoCompleteAfter) {
}
