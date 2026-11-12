package com.mamashop.common.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

class ImageTypeTest {

    @Test
    void detectsJpegPngWebp() {
        assertThat(ImageType.detect(bytes(0xFF, 0xD8, 0xFF, 0xE0, 0, 0))).contains(ImageType.JPEG);
        assertThat(ImageType.detect(bytes(0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0))).contains(ImageType.PNG);
        assertThat(ImageType.detect(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'E', 'B', 'P', 0))).contains(ImageType.WEBP);
    }

    @Test
    void rejectsOtherContent() {
        // HTML 改名成 .jpg：副檔名騙不過內容檢查
        assertThat(ImageType.detect("<html><script>alert(1)</script>".getBytes(StandardCharsets.UTF_8))).isEmpty();
        // GIF（不在允許清單）
        assertThat(ImageType.detect("GIF89a....".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        // RIFF 但不是 WEBP（例如 WAV 音檔）
        assertThat(ImageType.detect(bytes('R', 'I', 'F', 'F', 1, 2, 3, 4, 'W', 'A', 'V', 'E'))).isEmpty();
        // 太短、空的
        assertThat(ImageType.detect(bytes(0xFF, 0xD8))).isEmpty();
        assertThat(ImageType.detect(new byte[0])).isEmpty();
        assertThat(ImageType.detect(null)).isEmpty();
    }

    private static byte[] bytes(int... values) {
        byte[] result = new byte[values.length];
        for (int i = 0; i < values.length; i++) {
            result[i] = (byte) values[i];
        }
        return result;
    }
}
