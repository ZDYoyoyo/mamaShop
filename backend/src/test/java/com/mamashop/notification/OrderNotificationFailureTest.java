package com.mamashop.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.after;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.notification.config.NotificationConfig;
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
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.core.AmqpAdmin;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/**
 * 通知失敗處理（M03）：寄信失敗 → 重試 → 還是失敗就移到失敗佇列。
 *
 * <p>用真的 RabbitMQ；寄信換成 Mockito，控制「失敗幾次」。重試間隔縮短成 100 ms，測試不用等太久。
 */
@SpringBootTest(properties = {
        "app.notification.enabled=true",
        "spring.rabbitmq.listener.simple.auto-startup=true",
        "spring.rabbitmq.listener.simple.retry.initial-interval=100ms",
        "spring.rabbitmq.listener.simple.retry.multiplier=1",
        "spring.mail.host=localhost"})
class OrderNotificationFailureTest {

    @Autowired private OrderService orderService;
    @Autowired private AmqpAdmin amqpAdmin;
    @Autowired private RabbitTemplate rabbitTemplate;
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
        amqpAdmin.purgeQueue(NotificationConfig.QUEUE, false);
        amqpAdmin.purgeQueue(NotificationConfig.FAILED_QUEUE, false);
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("失敗測試-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("失敗測試包");
        product.setPrice(new BigDecimal("300.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = new User();
        customer.setEmail("failure-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("失敗測試");
        customer.setRole(Role.USER);
        customer = userRepository.save(customer);
    }

    @AfterEach
    void cleanUp() {
        amqpAdmin.purgeQueue(NotificationConfig.FAILED_QUEUE, false);
        jdbc.update("DELETE FROM order_status_histories WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM orders WHERE user_id = ?", customer.getId());
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", customer.getId());
    }

    @Test
    void temporaryFailure_succeedsOnRetry() {
        // 第一次寄信失敗（寄信伺服器暫時連不上），第二次成功
        doThrow(new MailSendException("SMTP 暫時無法連線")).doNothing()
                .when(mailSender).send(any(SimpleMailMessage.class));

        orderService.createOrder(customer.getId(), request());

        verify(mailSender, timeout(5_000).times(2)).send(any(SimpleMailMessage.class));
        verify(mailSender, after(1_000).times(2)).send(any(SimpleMailMessage.class));
        assertThat(rabbitTemplate.receive(NotificationConfig.FAILED_QUEUE)).isNull();
    }

    @Test
    void permanentFailure_movesToFailedQueue_withErrorDetails() {
        doThrow(new MailSendException("SMTP 帳密錯誤")).when(mailSender).send(any(SimpleMailMessage.class));

        String orderNo = orderService.createOrder(customer.getId(), request()).orderNo();

        // 共試 3 次（第一次＋重試 2 次）
        verify(mailSender, timeout(5_000).times(3)).send(any(SimpleMailMessage.class));
        // 然後移到失敗佇列，不是消失
        Message failed = await().atMost(Duration.ofSeconds(5))
                .until(() -> rabbitTemplate.receive(NotificationConfig.FAILED_QUEUE), m -> m != null);
        assertThat(new String(failed.getBody(), StandardCharsets.UTF_8)).contains(orderNo).contains("CREATED");
        assertThat(failed.getMessageProperties().<String>getHeader(RepublishMessageRecoverer.X_EXCEPTION_MESSAGE))
                .contains("SMTP 帳密錯誤");
        assertThat(failed.getMessageProperties().<String>getHeader(RepublishMessageRecoverer.X_ORIGINAL_ROUTING_KEY))
                .isEqualTo("order.created");
        // 失敗後不會再試
        verify(mailSender, after(1_000).times(3)).send(any(SimpleMailMessage.class));
    }

    private CreateOrderRequest request() {
        return new CreateOrderRequest(List.of(new CreateOrderRequest.Item(product.getId(), 1)),
                ShippingMethodCode.CVS_PICKUP, "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null);
    }
}
