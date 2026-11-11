package com.mamashop.security;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/** 對應 app.cors.*；環境變數用逗號分隔多個網址，Spring 會自動轉成 List。 */
@ConfigurationProperties("app.cors")
public record CorsProperties(List<String> allowedOrigins) {
}
