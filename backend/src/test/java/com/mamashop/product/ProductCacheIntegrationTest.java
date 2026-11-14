package com.mamashop.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamashop.category.dto.CategoryRequest;
import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.category.service.CategoryService;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.cache.ProductCacheNames;
import com.mamashop.product.dto.ProductUpdateRequest;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.product.service.AdminProductService;
import com.mamashop.product.service.ProductService;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 商品快取（R01）：用真的 Redis 測試「有快取」與「該清的時候有清」。
 *
 * <p>其他測試預設不快取（src/test/resources/application.properties），這裡用 properties 打開。
 * 需要 Redis：本機 docker compose up -d redis；CI 用 service container。
 *
 * <p>驗證「真的有快取」的方法：用 SQL 直接改資料庫（不經過程式，所以不會清快取），
 * 再查一次——還是舊資料，代表是從快取拿的。
 */
@SpringBootTest(properties = "spring.cache.type=redis")
class ProductCacheIntegrationTest {

    @Autowired private ProductService productService;
    @Autowired private AdminProductService adminProductService;
    @Autowired private CategoryService categoryService;
    @Autowired private OrderService orderService;
    @Autowired private CacheManager cacheManager;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category category;
    private Product product;
    private User customer;

    @BeforeEach
    void setUp() {
        clearCaches();
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("快取測試-" + suffix);
        category = categoryRepository.save(category);

        product = new Product();
        product.setCategory(category);
        product.setName("快取測試包");
        product.setPrice(new BigDecimal("890.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);

        customer = new User();
        customer.setEmail("cache-test-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("快取測試");
        customer.setRole(Role.USER);
        customer = userRepository.save(customer);
    }

    @AfterEach
    void cleanUp() {
        clearCaches();
        jdbc.update("DELETE FROM order_status_histories WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM orders WHERE user_id = ?", customer.getId());
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", customer.getId());
    }

    @Test
    void usesRedis() {
        assertThat(cacheManager).isInstanceOf(RedisCacheManager.class);
    }

    // ===== 詳情 =====

    @Test
    void detail_isCached() {
        assertThat(productService.getOnSaleProduct(product.getId()).name()).isEqualTo("快取測試包");
        renameBySql("直接改資料庫");

        // 繞過程式改資料庫不會清快取：拿到的還是快取裡的舊資料，證明真的有快取
        assertThat(productService.getOnSaleProduct(product.getId()).name()).isEqualTo("快取測試包");
    }

    @Test
    void detail_isEvicted_whenAdminUpdatesProduct() {
        productService.getOnSaleProduct(product.getId());

        adminProductService.update(product.getId(),
                new ProductUpdateRequest(category.getId(), "改名後的包", null, new BigDecimal("990.00")));

        var detail = productService.getOnSaleProduct(product.getId());
        assertThat(detail.name()).isEqualTo("改名後的包");
        assertThat(detail.price()).isEqualByComparingTo("990");
    }

    @Test
    void detail_isEvicted_whenTakenOffSale() {
        productService.getOnSaleProduct(product.getId());

        adminProductService.changeStatus(product.getId(), ProductStatus.OFF_SALE);

        // 下架後前台要立刻看不到（快取沒清的話，下架的商品還會被看到、被加入購物車）
        assertThatThrownBy(() -> productService.getOnSaleProduct(product.getId()))
                .hasMessageContaining("找不到商品");
    }

    // ===== 庫存：下單、取消 =====

    @Test
    void stock_isUpdated_afterOrderAndCancel() {
        assertThat(productService.getOnSaleProduct(product.getId()).stock()).isEqualTo(10);

        String orderNo = placeOrder(3).orderNo();
        assertThat(productService.getOnSaleProduct(product.getId()).stock()).isEqualTo(7);

        orderService.cancelMyOrder(customer.getId(), orderNo);
        assertThat(productService.getOnSaleProduct(product.getId()).stock()).isEqualTo(10);
    }

    @Test
    void failedOrder_doesNotEvict() {
        productService.getOnSaleProduct(product.getId());
        renameBySql("直接改資料庫");

        // 庫存不足 → 整筆交易 rollback → 不清快取（資料沒變，不需要清）
        assertThatThrownBy(() -> placeOrder(99)).isInstanceOf(BusinessException.class);

        assertThat(productService.getOnSaleProduct(product.getId()).name()).isEqualTo("快取測試包");
    }

    // ===== 列表 =====

    @Test
    void list_isCached_andEvicted_whenStockAdjusted() {
        assertThat(firstItemStock()).isEqualTo(10);
        jdbc.update("UPDATE products SET stock = 3 WHERE id = ?", product.getId());
        assertThat(firstItemStock()).isEqualTo(10); // 快取

        adminProductService.adjustStock(product.getId(), 5);

        assertThat(firstItemStock()).isEqualTo(8); // 3 + 5，快取已清
    }

    @Test
    void keywordSearch_isNotCached() {
        assertThat(productService.searchOnSaleProducts(null, "快取測試", 1, 12).items()).hasSize(1);
        renameBySql("別的名字");

        assertThat(productService.searchOnSaleProducts(null, "快取測試", 1, 12).items()).isEmpty();
    }

    // ===== 分類 =====

    @Test
    void categoryRename_evictsDetails() {
        productService.getOnSaleProduct(product.getId());

        categoryService.update(category.getId(), new CategoryRequest("新分類名-" + UUID.randomUUID().toString().substring(0, 8), 0));

        assertThat(productService.getOnSaleProduct(product.getId()).categoryName()).startsWith("新分類名-");
    }

    private int firstItemStock() {
        return productService.searchOnSaleProducts(category.getId(), null, 1, 12).items().getFirst().stock();
    }

    private void renameBySql(String name) {
        jdbc.update("UPDATE products SET name = ? WHERE id = ?", name, product.getId());
    }

    private com.mamashop.order.dto.OrderDetailResponse placeOrder(int quantity) {
        return orderService.createOrder(customer.getId(), new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(product.getId(), quantity)), ShippingMethodCode.CVS_PICKUP,
                "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null));
    }

    private void clearCaches() {
        cacheManager.getCache(ProductCacheNames.DETAIL).clear();
        cacheManager.getCache(ProductCacheNames.LIST).clear();
    }
}
