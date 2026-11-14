package com.mamashop.notification.dto;

import com.mamashop.order.event.OrderEventType;

/**
 * 放進 RabbitMQ 的訊息（M01），以 JSON 傳送。
 *
 * <p>只放訂單編號與事件種類，不放訂單內容：消費者寄信時再查資料庫，
 * 拿到的是最新的資料（例如收件人有更正）；訊息也比較小、不含個資。
 */
public record OrderNotificationMessage(String orderNo, OrderEventType type) {
}
