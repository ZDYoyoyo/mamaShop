package com.mamashop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.anonymous;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Random;
import java.util.stream.Stream;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 圖片上傳。這個測試刻意「不」加 @Transactional：
 * 舊圖片要在交易 commit 之後才刪除，測試交易最後會 rollback，就測不到這個行為。
 * 所以資料改在 @AfterEach 自己清掉，圖片存到暫存目錄。
 */
@SpringBootTest(properties = {
        "app.upload.dir=${java.io.tmpdir}/mamashop-test-uploads",
        // 程式內的大小檢查設小一點，方便測「檔案太大」
        "app.upload.max-image-size=20KB"
})
@AutoConfigureMockMvc
@WithMockUser(roles = "ADMIN")
class ProductImageUploadTest {

    private static final Path UPLOAD_DIR = Paths.get(System.getProperty("java.io.tmpdir"), "mamashop-test-uploads");

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ProductRepository productRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("圖片測試分類");
        category = categoryRepository.save(category);

        product = new Product();
        product.setCategory(category);
        product.setName("圖片測試商品");
        product.setPrice(new BigDecimal("100"));
        product.setStock(1);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
    }

    @AfterEach
    void cleanUp() {
        productRepository.deleteById(product.getId());
        categoryRepository.deleteById(category.getId());
    }

    @Test
    void uploadPng_savesWithUuidName_andIsPubliclyReadable() throws Exception {
        String body = upload(new MockMultipartFile("file", "我的照片.png", "image/png", png(10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.imageUrl").value(org.hamcrest.Matchers.matchesPattern(
                        "/uploads/products/[0-9a-f\\-]{36}\\.png")))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String imageUrl = JsonPath.read(body, "$.data.imageUrl");

        // 檔案真的存在上傳目錄
        assertThat(UPLOAD_DIR.resolve(imageUrl.substring("/uploads/".length()))).exists();

        // 不用登入就能讀圖片，Content-Type 正確
        mockMvc.perform(get(imageUrl).with(anonymous()))
                .andExpect(status().isOk())
                .andExpect(content().contentType("image/png"));

        // 前台商品 API 也回傳同一個網址
        mockMvc.perform(get("/api/products/" + product.getId()))
                .andExpect(jsonPath("$.data.imageUrl").value(imageUrl));
    }

    @Test
    void replaceImage_deletesOldFileAfterCommit() throws Exception {
        String first = imageUrlOf(upload(new MockMultipartFile("file", "a.png", "image/png", png(10))));
        Path firstFile = UPLOAD_DIR.resolve(first.substring("/uploads/".length()));
        assertThat(firstFile).exists();

        String second = imageUrlOf(upload(new MockMultipartFile("file", "b.png", "image/png", png(12))));

        assertThat(second).isNotEqualTo(first);
        assertThat(firstFile).doesNotExist();
        assertThat(UPLOAD_DIR.resolve(second.substring("/uploads/".length()))).exists();
    }

    @Test
    void fakeImage_isRejected_andNothingIsSaved() throws Exception {
        long filesBefore = countFiles();
        // 副檔名和 Content-Type 都說是 jpg，但內容其實是 HTML
        byte[] html = "<html><script>alert('xss')</script></html>".getBytes(StandardCharsets.UTF_8);

        upload(new MockMultipartFile("file", "cat.jpg", "image/jpeg", html))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("INVALID_IMAGE"));

        assertThat(countFiles()).isEqualTo(filesBefore);
        assertThat(productRepository.findById(product.getId()).orElseThrow().getImagePath()).isNull();
    }

    @Test
    void tooLargeImage_returns413() throws Exception {
        // 隨機像素的 PNG 幾乎無法壓縮，200x200 大約 120KB，超過測試設定的 20KB
        upload(new MockMultipartFile("file", "big.png", "image/png", png(200)))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.error.code").value("FILE_TOO_LARGE"));
    }

    @Test
    void missingFile_returns400() throws Exception {
        mockMvc.perform(multipart("/api/admin/products/" + product.getId() + "/image"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("file"));
    }

    @Test
    @WithMockUser(roles = "USER")
    void customer_cannotUpload() throws Exception {
        upload(new MockMultipartFile("file", "a.png", "image/png", png(10)))
                .andExpect(status().isForbidden());
    }

    // ===== helpers =====

    private ResultActions upload(MockMultipartFile file) throws Exception {
        return mockMvc.perform(multipart("/api/admin/products/" + product.getId() + "/image").file(file));
    }

    private String imageUrlOf(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.imageUrl");
    }

    /** 產生一張真的 PNG（隨機像素） */
    private static byte[] png(int size) throws IOException {
        BufferedImage image = new BufferedImage(size, size, BufferedImage.TYPE_INT_RGB);
        Random random = new Random(size);
        for (int x = 0; x < size; x++) {
            for (int y = 0; y < size; y++) {
                image.setRGB(x, y, random.nextInt());
            }
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }

    private static long countFiles() throws IOException {
        Path dir = UPLOAD_DIR.resolve("products");
        if (!Files.exists(dir)) {
            return 0;
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.count();
        }
    }
}
