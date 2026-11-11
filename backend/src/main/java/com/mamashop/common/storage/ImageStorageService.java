package com.mamashop.common.storage;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

/**
 * 把上傳的圖片存到伺服器目錄（app.upload.dir）。
 *
 * <ul>
 *   <li>檔名一律用 UUID 重新命名：不使用上傳者的檔名，避免路徑攻擊（../../etc/passwd）、
 *       中文或特殊字元、同名覆蓋，也不會洩漏原始檔名</li>
 *   <li>副檔名由實際內容決定（{@link ImageType}），不是上傳者說了算</li>
 *   <li>資料庫只存相對路徑（例如 products/xxx.jpg），之後搬目錄或改用雲端儲存只要改這裡</li>
 * </ul>
 */
@Service
public class ImageStorageService {

    private static final Logger log = LoggerFactory.getLogger(ImageStorageService.class);

    private final Path rootDir;
    private final long maxImageBytes;

    public ImageStorageService(UploadProperties properties) {
        this.rootDir = properties.rootDir();
        this.maxImageBytes = properties.maxImageSize().toBytes();
        try {
            Files.createDirectories(rootDir);
        } catch (IOException e) {
            throw new UncheckedIOException("無法建立上傳目錄 " + rootDir, e);
        }
        log.info("上傳檔案目錄：{}", rootDir);
    }

    /**
     * 驗證並儲存圖片。
     *
     * @param subDir 子目錄，例如 "products"
     * @return 相對於上傳目錄的路徑，例如 "products/3f2a...c1.jpg"
     */
    public String storeImage(MultipartFile file, String subDir) {
        if (file == null || file.isEmpty()) {
            throw new BusinessException(ErrorCode.INVALID_IMAGE, "請選擇要上傳的圖片");
        }
        // Tomcat 已經用 spring.servlet.multipart.max-file-size 擋過一次，這裡再檢查一次（縱深防禦）
        if (file.getSize() > maxImageBytes) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        ImageType type = ImageType.detect(bytes)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_IMAGE));

        String relativePath = subDir + "/" + UUID.randomUUID() + type.extension();
        Path target = resolve(relativePath);
        try {
            Files.createDirectories(target.getParent());
            // CREATE_NEW：檔案已存在就失敗，絕不覆蓋既有檔案
            Files.write(target, bytes, StandardOpenOption.CREATE_NEW);
        } catch (IOException e) {
            throw new UncheckedIOException("儲存圖片失敗：" + target, e);
        }
        return relativePath;
    }

    /** 刪除檔案；失敗只記 log，不影響主要流程（多一個沒用到的檔案不會壞事） */
    public void delete(String relativePath) {
        if (relativePath == null) {
            return;
        }
        try {
            Files.deleteIfExists(resolve(relativePath));
        } catch (IOException | IllegalArgumentException e) {
            log.warn("刪除檔案失敗：{}", relativePath, e);
        }
    }

    /** 相對路徑轉成實際路徑，並確認沒有跑出上傳目錄 */
    Path resolve(String relativePath) {
        Path path = rootDir.resolve(relativePath).normalize();
        if (!path.startsWith(rootDir)) {
            throw new IllegalArgumentException("路徑不在上傳目錄內：" + relativePath);
        }
        return path;
    }
}
