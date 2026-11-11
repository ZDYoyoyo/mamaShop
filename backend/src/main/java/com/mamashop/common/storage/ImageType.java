package com.mamashop.common.storage;

import java.util.Optional;

/**
 * 允許上傳的圖片格式。
 *
 * <p>用檔案開頭的「魔術位元組」（magic bytes）判斷真正的格式，不相信副檔名或 Content-Type：
 * 這兩個都是上傳者可以任意填的，例如把一個 HTML 檔改名成 cat.jpg。
 */
public enum ImageType {

    JPEG(".jpg"),
    PNG(".png"),
    WEBP(".webp");

    private final String extension;

    ImageType(String extension) {
        this.extension = extension;
    }

    public String extension() {
        return extension;
    }

    public static Optional<ImageType> detect(byte[] bytes) {
        if (startsWith(bytes, 0, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0, 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        // WebP：開頭是 "RIFF"，第 8～11 byte 是 "WEBP"
        if (startsWith(bytes, 0, 'R', 'I', 'F', 'F') && startsWith(bytes, 8, 'W', 'E', 'B', 'P')) {
            return Optional.of(WEBP);
        }
        return Optional.empty();
    }

    private static boolean startsWith(byte[] bytes, int offset, int... expected) {
        if (bytes == null || bytes.length < offset + expected.length) {
            return false;
        }
        for (int i = 0; i < expected.length; i++) {
            if ((bytes[offset + i] & 0xFF) != expected[i]) {
                return false;
            }
        }
        return true;
    }
}
