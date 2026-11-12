package com.mamashop.order.dto;

import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 後台訂單列表的一筆。
 *
 * @param version 直接在列表上操作（例如一鍵確認）時要帶回來
 */
public record AdminOrderSummaryResponse(
        String orderNo,
        OrderStatus status,
        PaymentStatus paymentStatus,
        ShippingMethodCode shippingMethod,
        String customerName,
        String recipientName,
        BigDecimal totalAmount,
        int totalQuantity,
        Instant createdAt,
        long version) {

    public static AdminOrderSummaryResponse from(Order order) {
        return new AdminOrderSummaryResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getShippingMethod(),
                order.getUser().getName(),
                order.getRecipientName(),
                order.getTotalAmount(),
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getCreatedAt(),
                order.getVersion());
    }
}
