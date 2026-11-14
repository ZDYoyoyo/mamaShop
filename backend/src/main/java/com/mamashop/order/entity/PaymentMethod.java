package com.mamashop.order.entity;

public enum PaymentMethod {
    /** 貨到付款（宅配貨到付款／超商取貨付款） */
    COD,
    /** 線上付款（P01，綠界信用卡）：訂單從「待付款」開始，金流通知付款成功後才進入備貨 */
    ONLINE
}
