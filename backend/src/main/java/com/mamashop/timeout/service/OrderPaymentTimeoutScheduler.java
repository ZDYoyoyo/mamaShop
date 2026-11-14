package com.mamashop.timeout.service;

import com.mamashop.order.event.OrderAwaitingPaymentEvent;
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
 * 線上付款訂單建立 → 排定「N 分鐘後還沒付款就自動取消」（P01，用 M02 的 TTL＋死信佇列）。
 *
 * <p>為什麼要自動取消：下單時已經扣了庫存，顧客不付款、也不取消，那些商品就一直被佔住，別人買不到。
 */
@Component
public class OrderPaymentTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentTimeoutScheduler.class);

    private final RabbitTemplate rabbitTemplate;
    private final OrderTimeoutProperties properties;

    public OrderPaymentTimeoutScheduler(RabbitTemplate rabbitTemplate, OrderTimeoutProperties properties) {
        this.rabbitTemplate = rabbitTemplate;
        this.properties = properties;
    }

    @Async
    @TransactionalEventListener
    public void onAwaitingPayment(OrderAwaitingPaymentEvent event) {
        if (!properties.enabled()) {
            return;
        }
        String ttlMillis = String.valueOf(properties.paymentTimeout().toMillis());
        try {
            rabbitTemplate.convertAndSend(OrderTimeoutConfig.EXCHANGE, OrderTimeoutConfig.PAYMENT_TIMEOUT_WAIT_KEY,
                    new OrderTimeoutMessage(event.orderNo()), message -> {
                        message.getMessageProperties().setExpiration(ttlMillis);
                        return message;
                    });
            log.info("訂單 {} 已排定 {} 後未付款自動取消", event.orderNo(), properties.paymentTimeout());
        } catch (AmqpException e) {
            // 庫存會一直被佔住，要讓家人知道（ERROR）：到後台手動取消
            log.error("訂單 {} 排定未付款自動取消失敗（RabbitMQ 連不上），逾時後請到後台手動取消：{}",
                    event.orderNo(), e.toString());
        }
    }
}
