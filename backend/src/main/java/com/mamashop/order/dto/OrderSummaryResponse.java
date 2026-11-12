package com.mamashop.order.dto;

import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * 訂單列表的一筆。
 *
 * @param firstProductName 第一個商品名稱，列表顯示「帆布托特包 等 3 件」
 * @param totalQuantity    商品總件數
 */
public record OrderSummaryResponse(
        String orderNo,
        OrderStatus status,
        PaymentStatus paymentStatus,
        ShippingMethodCode shippingMethod,
        BigDecimal totalAmount,
        String firstProductName,
        int totalQuantity,
        Instant createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getPaymentStatus(),
                order.getShippingMethod(),
                order.getTotalAmount(),
                order.getItems().isEmpty() ? null : order.getItems().get(0).getProductName(),
                order.getItems().stream().mapToInt(OrderItem::getQuantity).sum(),
                order.getCreatedAt());
    }
}
