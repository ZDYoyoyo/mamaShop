package com.mamashop.order.entity;

import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * 訂單狀態（出貨流程）。付款狀態另外由 {@link PaymentStatus} 記錄。
 *
 * <p><b>狀態機集中在這裡</b>：哪個狀態可以轉到哪個狀態，只在 {@link #canTransitionTo} 定義一次。
 * 顧客取消（B09）、後台確認／出貨／完成（B10）都經過 {@link Order#transitionTo}，
 * 不會有某個 Service 自己直接 setStatus 繞過規則。
 *
 * <pre>
 * PENDING_PAYMENT ─┐
 *                  ├─→ PROCESSING ─→ SHIPPED ─→ DELIVERED ─→ COMPLETED
 * PENDING_CONFIRM ─┘        │           │           │
 *        │                  │           └─────┬─────┘
 *        └──────────────────┴─→ CANCELLED     └─→ RETURNED
 * </pre>
 */
public enum OrderStatus {
    /** 等待線上付款（接金流後才使用） */
    PENDING_PAYMENT("待付款"),
    /** 待確認：貨到付款訂單建立後的初始狀態 */
    PENDING_CONFIRM("待確認"),
    /** 已確認、備貨中（待出貨） */
    PROCESSING("備貨中"),
    /** 已出貨 */
    SHIPPED("已出貨"),
    /** 已送達／已到超商門市 */
    DELIVERED("已送達"),
    /** 已完成 */
    COMPLETED("已完成"),
    /** 已取消 */
    CANCELLED("已取消"),
    /** 退回／超商未取件 */
    RETURNED("已退回");

    private static final Map<OrderStatus, Set<OrderStatus>> TRANSITIONS = Map.of(
            PENDING_PAYMENT, EnumSet.of(PROCESSING, CANCELLED),
            PENDING_CONFIRM, EnumSet.of(PROCESSING, CANCELLED),
            PROCESSING, EnumSet.of(SHIPPED, CANCELLED),
            SHIPPED, EnumSet.of(DELIVERED, RETURNED),
            DELIVERED, EnumSet.of(COMPLETED, RETURNED),
            COMPLETED, EnumSet.noneOf(OrderStatus.class),
            CANCELLED, EnumSet.noneOf(OrderStatus.class),
            RETURNED, EnumSet.noneOf(OrderStatus.class));

    /** 顧客可以自己取消的狀態：家人還沒確認之前 */
    private static final Set<OrderStatus> CUSTOMER_CANCELLABLE = EnumSet.of(PENDING_PAYMENT, PENDING_CONFIRM);

    private final String label;

    OrderStatus(String label) {
        this.label = label;
    }

    /** 中文名稱，用在給使用者看的訊息 */
    public String label() {
        return label;
    }

    public boolean canTransitionTo(OrderStatus target) {
        return TRANSITIONS.get(this).contains(target);
    }

    /** 顧客自行取消的限制比較嚴格；管理員在出貨前都能取消（由 canTransitionTo 決定） */
    public boolean isCancellableByCustomer() {
        return CUSTOMER_CANCELLABLE.contains(this);
    }

    /** 已經結束、不會再變動的狀態 */
    public boolean isFinal() {
        return TRANSITIONS.get(this).isEmpty();
    }
}
