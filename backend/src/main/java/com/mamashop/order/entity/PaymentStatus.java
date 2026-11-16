package com.mamashop.order.entity;

/**
 * 付款狀態。和 {@link OrderStatus}（出貨流程）分成兩個欄位：
 * 貨到付款是「先出貨、後收錢」，線上付款是「先收錢、後出貨」，拆開後兩種付款方式共用同一套出貨流程。
 */
public enum PaymentStatus {
    /** 未付款 */
    UNPAID,
    /**
     * 已付款。貨到付款：訂單完成時（顧客簽收／取件）；線上付款：金流通知付款成功時（P01）。
     * 「已取消或已退回＋已付款」代表收了錢但不會出貨，需要退款（後台訂單詳情頁有提醒）
     */
    PAID,
    /**
     * 已退款。退款本身是到綠界廠商後台人工處理（沒有串退款 API）；
     * 退完後家人在後台按「標記已退款」才會變成這個值（AdminOrderService#markRefunded），程式不會自動設定
     */
    REFUNDED
}
