package com.mamashop.timeout;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentStatus;
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
import java.util.Properties;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.rabbit.core.RabbitAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 延遲任務（M02）：已送達 → 等待佇列 → TTL 到期 → 死信 → 處理佇列 → 自動完成。
 *
 * <p>用真的 RabbitMQ，把「7 天」縮短成 2 秒。非同步用 Awaitility 等到狀態改變（最多 10 秒），不用固定 sleep。
 */
@SpringBootTest(properties = {
        "app.order-timeout.enabled=true",
        "app.order-timeout.auto-complete-after=2s",
        "spring.rabbitmq.listener.simple.auto-startup=true"})
class OrderAutoCompleteIntegrationTest {

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
        amqpAdmin.purgeQueue(OrderTimeoutConfig.AUTO_COMPLETE_WAIT_QUEUE, false);
        amqpAdmin.purgeQueue(OrderTimeoutConfig.AUTO_COMPLETE_QUEUE, false);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("自動完成測試-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("自動完成測試包");
        product.setPrice(new BigDecimal("500.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = new User();
        customer.setEmail("auto-complete-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("自動完成測試");
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
    void homeDelivery_delivered_isCompletedAfterDelay() {
        String orderNo = deliveredOrder(ShippingMethodCode.HOME_DELIVERY);

        // 先在等待佇列裡放著（還沒到期）
        await().atMost(Duration.ofSeconds(5)).until(() -> messageCount(OrderTimeoutConfig.AUTO_COMPLETE_WAIT_QUEUE) == 1);
        assertThat(status(orderNo)).isEqualTo(OrderStatus.DELIVERED);

        // 2 秒到期 → 死信 → 處理佇列 → 自動完成
        await().atMost(Duration.ofSeconds(10)).until(() -> status(orderNo) == OrderStatus.COMPLETED);
        AdminOrderDetailResponse detail = adminOrderService.get(orderNo);
        assertThat(detail.order().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        AdminOrderDetailResponse.HistoryResponse last = detail.histories().getLast();
        assertThat(last.toStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(last.operatorName()).isEqualTo("系統");
        assertThat(last.note()).contains("系統自動完成");
        assertThat(messageCount(OrderTimeoutConfig.AUTO_COMPLETE_WAIT_QUEUE)).isZero();
    }

    @Test
    void completedManuallyBeforeDelay_isLeftAlone() {
        String orderNo = deliveredOrder(ShippingMethodCode.HOME_DELIVERY);
        // 家人在時間到之前就手動完成
        adminOrderService.complete(orderNo, 3, customer.getId(), "已收款");

        // 等訊息到期、被處理完（兩個佇列都空了）
        await().atMost(Duration.ofSeconds(10)).until(() ->
                messageCount(OrderTimeoutConfig.AUTO_COMPLETE_WAIT_QUEUE) == 0
                        && messageCount(OrderTimeoutConfig.AUTO_COMPLETE_QUEUE) == 0
                        && waitedAtLeast(Duration.ofSeconds(3)));
        AdminOrderDetailResponse detail = adminOrderService.get(orderNo);
        // 只有一筆「→ 已完成」，而且是家人做的
        assertThat(detail.histories()).filteredOn(h -> h.toStatus() == OrderStatus.COMPLETED)
                .singleElement().satisfies(h -> assertThat(h.note()).isEqualTo("已收款"));
    }

    @Test
    void cvsPickup_isNotScheduled() {
        // 超商「已送達」= 已到店，顧客還沒取貨付款，不自動完成
        String orderNo = deliveredOrder(ShippingMethodCode.CVS_PICKUP);

        await().pollDelay(Duration.ofSeconds(3)).atMost(Duration.ofSeconds(5)).until(() -> true);
        assertThat(status(orderNo)).isEqualTo(OrderStatus.DELIVERED);
        assertThat(messageCount(OrderTimeoutConfig.AUTO_COMPLETE_WAIT_QUEUE)).isZero();
    }

    // ===== helpers =====

    private long startedAt;

    private boolean waitedAtLeast(Duration duration) {
        return System.currentTimeMillis() - startedAt >= duration.toMillis();
    }

    /** 下單 → 確認 → 出貨 → 已送達（操作者：測試裡用同一個會員代替管理員） */
    private String deliveredOrder(ShippingMethodCode method) {
        String orderNo = orderService.createOrder(customer.getId(), request(method)).orderNo();
        adminOrderService.confirm(orderNo, 0, customer.getId(), null);
        adminOrderService.ship(orderNo, 1, "T1", customer.getId(), null);
        adminOrderService.deliver(orderNo, 2, customer.getId(), null);
        startedAt = System.currentTimeMillis();
        return orderNo;
    }

    private OrderStatus status(String orderNo) {
        return adminOrderService.get(orderNo).order().status();
    }

    private long messageCount(String queue) {
        Properties info = amqpAdmin.getQueueProperties(queue);
        return info == null ? -1 : ((Number) info.get(RabbitAdmin.QUEUE_MESSAGE_COUNT)).longValue();
    }

    private CreateOrderRequest request(ShippingMethodCode method) {
        List<CreateOrderRequest.Item> items = List.of(new CreateOrderRequest.Item(product.getId(), 1));
        return method == ShippingMethodCode.HOME_DELIVERY
                ? new CreateOrderRequest(items, method, "王小明", "0912345678", "台北市測試路 1 號", null, null, null, null)
                : new CreateOrderRequest(items, method, "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null);
    }
}
