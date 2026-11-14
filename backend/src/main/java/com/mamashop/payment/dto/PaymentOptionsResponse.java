package com.mamashop.payment.dto;

/**
 * 結帳頁要知道的付款設定。
 *
 * @param onlinePaymentEnabled  有沒有開線上付款
 * @param paymentTimeoutMinutes 線上付款要在幾分鐘內完成，逾時訂單自動取消
 */
public record PaymentOptionsResponse(boolean onlinePaymentEnabled, long paymentTimeoutMinutes) {
}
