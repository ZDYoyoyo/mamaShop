package com.mamashop.notification.service;

import com.mamashop.notification.config.NotificationConfig;
import com.mamashop.notification.config.NotificationProperties;
import com.mamashop.notification.dto.OrderNotificationMessage;
import com.mamashop.order.event.OrderEvent;
import java.util.Locale;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 訂單事件 → RabbitMQ（M01 的生產者）。
 *
 * <ul>
 *   <li><b>{@code @TransactionalEventListener}</b>（預設 AFTER_COMMIT）：訂單交易 commit 之後才送。
 *       交易 rollback（下單失敗）就不會送，顧客不會收到「訂單成立」卻查不到訂單</li>
 *   <li><b>{@code @Async}</b>：在另一個執行緒送，不讓顧客等。RabbitMQ 很慢或連不上時，
 *       下單的回應不會被拖住（連線逾時 2 秒，見 application.yml）</li>
 *   <li><b>送不出去只記 log</b>：通知失敗不能影響已經成立的訂單</li>
 * </ul>
 *
 * <p>⚠️ 已知的缺口：commit 之後、送進 RabbitMQ 之前，如果後端當機或 RabbitMQ 連不上，這則通知就遺失了
 * （訂單本身沒事）。要完全避免得用 Outbox 模式：把訊息和訂單寫在同一個交易，再由另一個程式送出。
 * 見 docs/NOTES.md 的 M01。
 */
@Component
public class OrderNotificationPublisher {

    private static final Logger log = LoggerFactory.getLogger(OrderNotificationPublisher.class);

    private final RabbitTemplate rabbitTemplate;
    private final NotificationProperties properties;

    public OrderNotificationPublisher(RabbitTemplate rabbitTemplate, NotificationProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onOrderEvent(OrderEvent event) {
        if (!properties.enabled()) {
            return;
        }
        String routingKey = NotificationConfig.ROUTING_KEY_PREFIX + event.type().name().toLowerCase(Locale.ROOT);
        try {
            rabbitTemplate.convertAndSend(NotificationConfig.EXCHANGE, routingKey,
                    new OrderNotificationMessage(event.orderNo(), event.type()));
        } catch (AmqpException e) {
            log.warn("訂單 {} 的通知（{}）送不進 RabbitMQ，這封信不會寄出：{}", event.orderNo(), event.type(), e.toString());
        }
    }
}
