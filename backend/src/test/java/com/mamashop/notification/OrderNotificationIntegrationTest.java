package com.mamashop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.notification.config.NotificationConfig;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.service.AdminOrderService;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 訂單通知（M01）：下單 → 交易 commit → RabbitMQ → 消費者 → 寄信。
 *
 * <p>用真的 RabbitMQ（本機 docker compose up -d rabbitmq；CI 有 service），寄信換成假的（Mockito），
 * 檢查「有沒有寄、寄給誰、主旨」。信件內容在 OrderEmailComposerTest 測。
 * 非同步：用 Mockito 的 timeout() 等到信寄出（最多 10 秒），而不是 sleep 固定時間。
 */
@SpringBootTest(properties = {
        "app.notification.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.mail.host=localhost"})
class OrderNotificationIntegrationTest {

    @Autowired private OrderService orderService;
    @Autowired private AdminOrderService adminOrderService;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private JavaMailSender mailSender;

    private Category category;
    private Product product;
    private User customer;

    @BeforeEach
    void setUp() {
        // 清掉其他人（例如本機開發時）留在佇列裡的訊息
        amqpAdmin.purgeQueue(NotificationConfig.QUEUE, false);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("通知測試-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("通知測試包");
        product.setPrice(new BigDecimal("890.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = new User();
        customer.setEmail("notify-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("通知測試");
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
    void orderCreatedConfirmedShipped_sendsThreeEmails() {
        String orderNo = orderService.createOrder(customer.getId(), request(2)).orderNo();
        assertThat(nextEmail().getSubject()).isEqualTo("[mamaShop] 訂單已成立（" + orderNo + "）");

        adminOrderService.confirm(orderNo, 0, customer.getId(), null); // 操作者：測試裡用同一個會員代替管理員
        assertThat(nextEmail().getSubject()).contains("訂單已確認").contains(orderNo);

        adminOrderService.ship(orderNo, 1, "T12345", customer.getId(), null);
        SimpleMailMessage shipped = nextEmail();
        assertThat(shipped.getSubject()).contains("訂單已出貨");
        assertThat(shipped.getText()).contains("物流單號：T12345");
        assertThat(shipped.getTo()).containsExactly(customer.getEmail());
    }

    @Test
    void failedOrder_sendsNothing() {
        // 庫存不足 → 交易 rollback → 事件不會被處理（AFTER_COMMIT），不會有「訂單成立」的信
        assertThatThrownBy(() -> orderService.createOrder(customer.getId(), request(99)))
                .isInstanceOf(BusinessException.class);

        verify(mailSender, after(2000).never()).send(Mockito.any(SimpleMailMessage.class));
    }

    private int sent = 0;

    /** 等下一封信寄出（最多 10 秒）並取回內容 */
    private SimpleMailMessage nextEmail() {
        sent++;
        ArgumentCaptor<SimpleMailMessage> captor = ArgumentCaptor.forClass(SimpleMailMessage.class);
        verify(mailSender, timeout(10_000).times(sent)).send(captor.capture());
        return captor.getAllValues().get(sent - 1);
    }

    private CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest(List.of(new CreateOrderRequest.Item(product.getId(), quantity)),
                ShippingMethodCode.CVS_PICKUP, "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null);
    }
}
