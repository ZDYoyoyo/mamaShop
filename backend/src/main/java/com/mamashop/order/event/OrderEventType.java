package com.mamashop.order.event;

/** 需要通知顧客的訂單事件（M01） */
public enum OrderEventType {
    /** 顧客下單 */
    CREATED,
    /** 管理員確認訂單（開始備貨） */
    CONFIRMED,
    /** 已出貨（有物流單號） */
    SHIPPED
}
