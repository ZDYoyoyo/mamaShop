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
 * <p>處理失敗（丟例外）時：application.yml 設定 default-requeue-rejected=false，
 * 訊息不會放回佇列（否則同一封信會無限重試、一直失敗）。M03 會加上重試與失敗佇列，
 * 讓失敗的通知不會就這樣不見。
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
