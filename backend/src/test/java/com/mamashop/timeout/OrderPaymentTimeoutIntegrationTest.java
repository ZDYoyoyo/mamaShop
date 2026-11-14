package com.mamashop.timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.service.AdminOrderService;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.timeout.config.OrderTimeoutConfig;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 逾時未付款自動取消（P01，用 M02 的 TTL＋死信佇列）：線上付款下單 → 2 秒沒付款 → 取消並歸還庫存。
 * 付款期限從 30 分鐘縮成 2 秒，真的 RabbitMQ。
 */
@SpringBootTest(properties = {
        "app.payment.ecpay.enabled=true",
        "app.payment.ecpay.merchant-id=3002607",
        "app.payment.ecpay.hash-key=test",
        "app.payment.ecpay.hash-iv=test",
        "app.order-timeout.enabled=true",
        "app.order-timeout.payment-timeout=2s",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
class OrderPaymentTimeoutIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private AdminOrderService adminOrderService;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category category;
    private Product product;
    private User customer;

    @BeforeEach
    void setUp() {
        amqpAdmin.purgeQueue(OrderTimeoutConfig.PAYMENT_TIMEOUT_WAIT_QUEUE, false);
        amqpAdmin.purgeQueue(OrderTimeoutConfig.PAYMENT_TIMEOUT_QUEUE, false);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("付款逾時測試-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("付款逾時測試包");
        product.setPrice(new BigDecimal("500.00"));
        product.setStock(5);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = new User();
        customer.setEmail("pay-timeout-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("付款逾時測試");
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
    void unpaidOrder_isCancelledAfterTimeout_andStockRestored() {
        String orderNo = orderService.createOrder(customer.getId(), request(PaymentMethod.ONLINE)).orderNo();
        assertThat(stock()).isEqualTo(3);

        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderNo) == OrderStatus.CANCELLED);

        assertThat(stock()).isEqualTo(5);
        AdminOrderDetailResponse.HistoryResponse last = adminOrderService.get(orderNo).histories().getLast();
        assertThat(last.operatorName()).isEqualTo("系統");
        assertThat(last.note()).contains("未付款，系統自動取消");
    }

    @Test
    void cashOnDeliveryOrder_isNotScheduled() {
        String orderNo = orderService.createOrder(customer.getId(), request(PaymentMethod.COD)).orderNo();

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> true);
        assertThat(status(orderNo)).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(stock()).isEqualTo(3);
    }

    private OrderStatus status(String orderNo) {
        return adminOrderService.get(orderNo).order().status();
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }

    private CreateOrderRequest request(PaymentMethod paymentMethod) {
        return new CreateOrderRequest(List.of(new CreateOrderRequest.Item(product.getId(), 2)),
                ShippingMethodCode.HOME_DELIVERY, "王小明", "0912345678", "台北市測試路 1 號", null, null, null, null,
                paymentMethod);
    }
}
