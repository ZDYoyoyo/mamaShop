package com.mamashop.order;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 訂單測試共用的資料建立與清除。
 * 訂單測試不能用 @Transactional 自動 rollback（要測真正的 commit／rollback 與併發），
 * 所以資料要自己建、自己清。
 */
class OrderTestData {

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final JdbcTemplate jdbc;

    private User user;
    private Category category;

    OrderTestData(UserRepository userRepository, CategoryRepository categoryRepository,
                  ProductRepository productRepository, JdbcTemplate jdbc) {
        this.userRepository = userRepository;
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.jdbc = jdbc;
    }

    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        user = new User();
        user.setEmail("order-test-" + suffix + "@example.com");
        user.setPasswordHash("not-used");
        user.setName("下單測試");
        user.setRole(Role.USER);
        user = userRepository.save(user);

        category = new Category();
        category.setName("訂單測試分類-" + suffix);
        category = categoryRepository.save(category);
    }

    Product product(String name, String price, int stock, ProductStatus status) {
        Product p = new Product();
        p.setCategory(category);
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setStock(stock);
        p.setStatus(status);
        return productRepository.save(p);
    }

    int stockOf(Product product) {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }

    int orderCount() {
        return jdbc.queryForObject("SELECT count(*) FROM orders WHERE user_id = ?", Integer.class, user.getId());
    }

    User user() {
        return user;
    }

    /** 依外鍵順序刪除：狀態紀錄 → 明細 → 訂單 → 商品 → 分類 → 會員 */
    void cleanUp() {
        Long userId = user.getId();
        jdbc.update("DELETE FROM order_status_histories WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", userId);
        jdbc.update("DELETE FROM orders WHERE user_id = ?", userId);
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", userId);
    }
}
