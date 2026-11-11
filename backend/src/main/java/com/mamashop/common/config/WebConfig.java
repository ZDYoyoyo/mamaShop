package com.mamashop.common.config;

import com.mamashop.common.storage.UploadProperties;
import java.time.Duration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * 讓上傳的圖片可以用網址讀取：GET /uploads/products/xxx.jpg → {app.upload.dir}/products/xxx.jpg
 *
 * <p>本機開發時由 Spring Boot 直接提供；正式環境（D03）可以改由 Nginx 直接讀同一個目錄，效率更好。
 */
@Configuration
@EnableConfigurationProperties(UploadProperties.class)
public class WebConfig implements WebMvcConfigurer {

    public static final String UPLOAD_URL_PREFIX = "/uploads/";

    private final UploadProperties uploadProperties;

    public WebConfig(UploadProperties uploadProperties) {
        this.uploadProperties = uploadProperties;
    }

    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        String location = uploadProperties.rootDir().toUri().toString();
        registry.addResourceHandler(UPLOAD_URL_PREFIX + "**")
                .addResourceLocations(location.endsWith("/") ? location : location + "/")
                // 檔名是 UUID，內容永遠不會變（換圖片就是新檔名），可以讓瀏覽器快取很久
                .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic());
    }
}
