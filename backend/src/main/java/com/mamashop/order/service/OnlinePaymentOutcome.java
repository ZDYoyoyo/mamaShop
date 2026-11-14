package com.mamashop.order.service;

/** 收到線上付款成功通知後，訂單這邊的處理結果（P01） */
public enum OnlinePaymentOutcome {
    /** 正常：待付款 → 備貨中、已付款 */
    CONFIRMED,
    /** 訂單已經取消（逾時或顧客取消）後才付款成功：訂單維持已取消，標記已付款，需要退款 */
    PAID_AFTER_CANCEL,
    /** 訂單已經付過款（同一張訂單開了兩個付款頁、兩次都付了）：需要退款其中一筆 */
    DUPLICATE_PAYMENT
}
