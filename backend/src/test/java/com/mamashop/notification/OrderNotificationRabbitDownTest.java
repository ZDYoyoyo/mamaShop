package com.mamashop.notification;

import static org.assertj.core.api.Assertions.assertThat;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
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
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * RabbitMQ 連不到時（M01）：下單照樣成功，而且不會被拖慢（通知在背景送，失敗只記 log）。
 * 用沒有人在聽的 port 模擬 RabbitMQ 掛掉。
 */
@SpringBootTest(properties = {"app.notification.enabled=true", "spring.rabbitmq.port=1"})
class OrderNotificationRabbitDownTest {

    @Autowired private OrderService orderService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category category;
    private Product product;
    private User customer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("MQ掛掉-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("MQ 掛掉測試");
        product.setPrice(new BigDecimal("100.00"));
        product.setStock(5);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = new User();
        customer.setEmail("mq-down-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("MQ 掛掉");
        customer.setRole(Role.USER);
        customer = userRepository.save(customer);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM order_status_histories WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM orders WHERE user_id = ?", customer.getId());
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", customer.getId());
    }

    @Test
    void orderStillSucceeds_andIsNotSlowedDown() {
        long start = System.nanoTime();

        String orderNo = orderService.createOrder(customer.getId(), new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(product.getId(), 1)), ShippingMethodCode.CVS_PICKUP,
                "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null)).orderNo();

        assertThat(orderNo).startsWith("M");
        // 送 RabbitMQ 是在另一個執行緒（@Async），下單不用等它連線逾時
        assertThat((System.nanoTime() - start) / 1_000_000).isLessThan(1500);
    }
}
