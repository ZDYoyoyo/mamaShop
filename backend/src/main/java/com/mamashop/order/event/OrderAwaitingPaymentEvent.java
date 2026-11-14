package com.mamashop.order.event;

/**
 * 線上付款的訂單建立了、正在等顧客付款（P01）。
 * 逾時自動處理（timeout 模組）聽這個事件，排定「N 分鐘後還沒付款就自動取消」。
 */
public record OrderAwaitingPaymentEvent(String orderNo) {
}
