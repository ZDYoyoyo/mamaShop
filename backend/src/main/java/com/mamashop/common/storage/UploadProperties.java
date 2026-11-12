package com.mamashop.common.storage;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.util.unit.DataSize;

/** 對應 app.upload.*。DataSize 可以直接寫 10MB、500KB 這種格式。 */
@ConfigurationProperties("app.upload")
public record UploadProperties(String dir, DataSize maxImageSize) {

    /** 上傳目錄的絕對路徑 */
    public Path rootDir() {
        return Paths.get(dir).toAbsolutePath().normalize();
    }
}
