package com.mamashop.payment.entity;

/** 一次付款嘗試的狀態（和訂單的 PaymentStatus 不同：一張訂單可以有好幾次嘗試） */
public enum PaymentAttemptStatus {
    /** 已建立、顧客在綠界付款頁，等綠界通知 */
    PENDING,
    /** 綠界通知付款成功 */
    PAID,
    /** 綠界通知付款失敗 */
    FAILED
}
