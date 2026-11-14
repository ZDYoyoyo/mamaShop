package com.mamashop.timeout.service;

import com.mamashop.order.service.AdminOrderService;
import com.mamashop.timeout.config.OrderTimeoutConfig;
import com.mamashop.timeout.config.OrderTimeoutProperties;
import com.mamashop.timeout.dto.OrderTimeoutMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * 付款期限到了：還沒付款就取消並歸還庫存（P01）。
 * 判斷與鎖定在 {@link AdminOrderService#autoCancelUnpaid}：已經付款或顧客自己取消了就略過（冪等）。
 */
@Component
public class OrderPaymentTimeoutListener {

    private static final Logger log = LoggerFactory.getLogger(OrderPaymentTimeoutListener.class);

    private final AdminOrderService adminOrderService;
    private final OrderTimeoutProperties properties;

    public OrderPaymentTimeoutListener(AdminOrderService adminOrderService, OrderTimeoutProperties properties) {
        this.adminOrderService = adminOrderService;
        this.properties = properties;
    }

    @RabbitListener(queues = OrderTimeoutConfig.PAYMENT_TIMEOUT_QUEUE)
    public void handle(OrderTimeoutMessage message) {
        String note = "超過 " + properties.paymentTimeout().toMinutes() + " 分鐘未付款，系統自動取消";
        if (adminOrderService.autoCancelUnpaid(message.orderNo(), note)) {
            log.info("訂單 {} 逾時未付款，已自動取消並歸還庫存", message.orderNo());
        } else {
            log.info("訂單 {} 已不是「待付款」（已付款或已取消），略過", message.orderNo());
        }
    }
}
