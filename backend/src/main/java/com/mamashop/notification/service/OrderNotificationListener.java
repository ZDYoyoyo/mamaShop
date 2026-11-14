package com.mamashop.notification.service;

import com.mamashop.notification.config.NotificationConfig;
import com.mamashop.notification.dto.OrderNotificationMessage;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.service.AdminOrderService;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * RabbitMQ → 寄信（M01 的消費者）。
 *
 * <p>和下單是不同的執行緒、不同的時間點：寄信慢（SMTP 常常要 1～2 秒）、寄信失敗，都不影響下單。
 * 這就是用佇列「解耦」的好處。
 *
 * <p>處理失敗（丟例外，例如寄信伺服器暫時連不上）時：先重試（M03，等 2 秒、6 秒，共 3 次），
 * 還是失敗就移到失敗佇列 mamashop.order-notifications.failed，不會就這樣不見（見 MessagingFailureConfig）。
 */
@Component
public class OrderNotificationListener {

    private final AdminOrderService adminOrderService;
    private final OrderEmailSender orderEmailSender;

    public OrderNotificationListener(AdminOrderService adminOrderService, OrderEmailSender orderEmailSender) {
        this.adminOrderService = adminOrderService;
        this.orderEmailSender = orderEmailSender;
    }

    @RabbitListener(queues = NotificationConfig.QUEUE)
    public void handle(OrderNotificationMessage message) {
        // 寄信時才查訂單：拿到最新資料（含顧客 Email）
        AdminOrderDetailResponse detail = adminOrderService.get(message.orderNo());
        orderEmailSender.send(message.type(), detail);
    }
}
