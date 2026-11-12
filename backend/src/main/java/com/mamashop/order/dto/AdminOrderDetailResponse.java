package com.mamashop.order.dto;

import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.OrderStatusHistory;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import java.time.Instant;
import java.util.List;

/**
 * 後台訂單詳情：顧客看得到的內容（order）＋下單會員＋狀態紀錄＋版本號。
 *
 * @param version 之後的操作（確認、出貨…）要帶回這個版本號
 */
public record AdminOrderDetailResponse(
        OrderDetailResponse order,
        CustomerInfo customer,
        List<HistoryResponse> histories,
        long version) {

    public static AdminOrderDetailResponse from(Order order, List<OrderStatusHistory> histories) {
        return new AdminOrderDetailResponse(
                OrderDetailResponse.from(order),
                CustomerInfo.from(order.getUser()),
                histories.stream().map(HistoryResponse::from).toList(),
                order.getVersion());
    }

    /** 下單會員（可能和收件人不同） */
    public record CustomerInfo(Long id, String name, String email, String phone) {

        static CustomerInfo from(User user) {
            return new CustomerInfo(user.getId(), user.getName(), user.getEmail(), user.getPhone());
        }
    }

    /** 一筆狀態變更：誰、什麼時候、從什麼改成什麼 */
    public record HistoryResponse(OrderStatus fromStatus, OrderStatus toStatus,
                                  String operatorName, Role operatorRole, String note, Instant createdAt) {

        static HistoryResponse from(OrderStatusHistory h) {
            User operator = h.getOperator();
            return new HistoryResponse(
                    h.getFromStatus(),
                    h.getToStatus(),
                    operator == null ? "系統" : operator.getName(),
                    operator == null ? null : operator.getRole(),
                    h.getNote(),
                    h.getCreatedAt());
        }
    }
}
