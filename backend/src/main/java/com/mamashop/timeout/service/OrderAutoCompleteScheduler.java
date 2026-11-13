package com.mamashop.timeout.service;

import com.mamashop.order.event.OrderDeliveredEvent;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.timeout.config.OrderTimeoutConfig;
import com.mamashop.timeout.config.OrderTimeoutProperties;
import com.mamashop.timeout.dto.OrderTimeoutMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 訂單送達 → 排定「N 天後自動完成」（M02 的生產者）。
 *
 * <p>和 M01 的通知一樣：交易 commit 之後（{@code @TransactionalEventListener}）、
 * 在另一個執行緒（{@code @Async}）送，RabbitMQ 連不上不影響管理員按「已送達」。
 *
 * <p><b>只排宅配訂單</b>：超商取貨的「已送達」是「已到店」，顧客還沒取貨付款，
 * 可能 7 天沒取、包裹退回（→ 已退回）。自動完成會把沒付錢的訂單標成已付款，所以超商訂單維持由家人手動完成。
 */
@Component
public class OrderAutoCompleteScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderAutoCompleteScheduler.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderTimeoutProperties properties;

    public OrderAutoCompleteScheduler(RabbitTemplate rabbitTemplate, OrderTimeoutProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onDelivered(OrderDeliveredEvent event) {
        if (!properties.enabled() || event.shippingMethod() != ShippingMethodCode.HOME_DELIVERY) {
            return;
        }
        String ttlMillis = String.valueOf(properties.autoCompleteAfter().toMillis());
        try {
            rabbitTemplate.convertAndSend(OrderTimeoutConfig.EXCHANGE, OrderTimeoutConfig.AUTO_COMPLETE_WAIT_KEY,
                    new OrderTimeoutMessage(event.orderNo()), message -> {
                        // 每則訊息自己的 TTL（毫秒，字串）。到期後被死信轉到處理佇列
                        message.getMessageProperties().setExpiration(ttlMillis);
                        return message;
                    });
            log.info("訂單 {} 已排定 {} 後自動完成", event.orderNo(), properties.autoCompleteAfter());
        } catch (AmqpException e) {
            log.warn("訂單 {} 排定自動完成失敗（RabbitMQ 連不上），需要手動完成：{}", event.orderNo(), e.toString());
        }
    }
}
