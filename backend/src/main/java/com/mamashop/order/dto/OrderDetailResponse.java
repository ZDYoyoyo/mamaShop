package com.mamashop.order.dto;

import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * 訂單明細（顧客看得到的內容）。
 * 對外用 orderNo 識別訂單，不暴露資料庫的流水號 id：流水號可以被猜（1、2、3…），也會透露訂單量。
 */
public record OrderDetailResponse(
        String orderNo,
        OrderStatus status,
        PaymentMethod paymentMethod,
        PaymentStatus paymentStatus,
        ShippingMethodCode shippingMethod,
        String recipientName,
        String recipientPhone,
        String shippingAddress,
        CvsBrand cvsBrand,
        String cvsStoreCode,
        String cvsStoreName,
        String note,
        String trackingNo,
        BigDecimal itemsAmount,
        BigDecimal shippingFee,
        BigDecimal totalAmount,
        List<ItemResponse> items,
        Instant createdAt,
        // 各狀態的時間：前端可以畫出「下單 → 確認 → 出貨 → 送達 → 完成」的進度
        Instant confirmedAt,
        Instant shippedAt,
        Instant deliveredAt,
        Instant completedAt,
        Instant cancelledAt,
        Instant returnedAt,
        Instant paidAt) {

    public static OrderDetailResponse from(Order order) {
        return new OrderDetailResponse(
                order.getOrderNo(),
                order.getStatus(),
                order.getPaymentMethod(),
                order.getPaymentStatus(),
                order.getShippingMethod(),
                order.getRecipientName(),
                order.getRecipientPhone(),
                order.getShippingAddress(),
                order.getCvsBrand(),
                order.getCvsStoreCode(),
                order.getCvsStoreName(),
                order.getNote(),
                order.getTrackingNo(),
                order.getItemsAmount(),
                order.getShippingFee(),
                order.getTotalAmount(),
                order.getItems().stream().map(ItemResponse::from).toList(),
                order.getCreatedAt(),
                order.getConfirmedAt(),
                order.getShippedAt(),
                order.getDeliveredAt(),
                order.getCompletedAt(),
                order.getCancelledAt(),
                order.getReturnedAt(),
                order.getPaidAt());
    }

    /** 明細：名稱、單價是下單當下的快照 */
    public record ItemResponse(Long productId, String productName, BigDecimal unitPrice, int quantity,
                               BigDecimal subtotal) {

        static ItemResponse from(OrderItem item) {
            return new ItemResponse(item.getProduct().getId(), item.getProductName(), item.getUnitPrice(),
                    item.getQuantity(), item.getSubtotal());
        }
    }
}
