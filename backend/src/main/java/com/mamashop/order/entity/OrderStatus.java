package com.mamashop.order.entity;

/**
 * 訂單狀態（出貨流程）。付款狀態另外由 {@link PaymentStatus} 記錄。
 * 狀態轉換規則（canTransitionTo）會在 B09／B10 實作。
 */
public enum OrderStatus {
    /** 等待線上付款（接金流後才使用） */
    PENDING_PAYMENT,
    /** 待確認：貨到付款訂單建立後的初始狀態 */
    PENDING_CONFIRM,
    /** 已確認、備貨中（待出貨） */
    PROCESSING,
    /** 已出貨 */
    SHIPPED,
    /** 已送達／已到超商門市 */
    DELIVERED,
    /** 已完成 */
    COMPLETED,
    /** 已取消 */
    CANCELLED,
    /** 退回／超商未取件 */
    RETURNED
}
