package com.mamashop.product;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.product.service.AdminProductService;
import com.mamashop.product.service.ProductService;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Redis 掛掉時網站照常運作（R01）：快取讀寫失敗只記 log，改查資料庫；商品修改也照樣成功。
 * 用一個沒有人在聽的 port 模擬 Redis 掛掉。
 */
@SpringBootTest(properties = {"spring.cache.type=redis", "spring.data.redis.port=1"})
class ProductCacheRedisDownTest {

    @Autowired private ProductService productService;
    @Autowired private AdminProductService adminProductService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category category;
    private Product product;

    @BeforeEach
    void setUp() {
        category = new Category();
        category.setName("Redis掛掉-" + UUID.randomUUID().toString().substring(0, 8));
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("Redis 掛掉測試");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
    }

    @Test
    void readsFallBackToDatabase_andWritesStillSucceed() {
        long start = System.nanoTime();

        assertThat(productService.getOnSaleProduct(product.getId()).name()).isEqualTo("Redis 掛掉測試");
        assertThat(productService.searchOnSaleProducts(category.getId(), null, 1, 12).items()).hasSize(1);
        // 後台修改（會清快取）也不能失敗
        assertThat(adminProductService.adjustStock(product.getId(), 3).stock()).isEqualTo(8);
        assertThat(productService.getOnSaleProduct(product.getId()).stock()).isEqualTo(8);

        // 每次快取操作最多等 0.5 秒（application.yml 的 timeout），不能把網站拖得很慢
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(10_000);
    }
}
