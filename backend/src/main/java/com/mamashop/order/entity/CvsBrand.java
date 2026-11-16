package com.mamashop.order.entity;

/** 超商品牌（超商取貨用）。 */
public enum CvsBrand {
    /** 7-ELEVEN */
    SEVEN,
    /** 全家 */
    FAMILY,
    /** 萊爾富 */
    HILIFE,
    /**
     * OK 超商。店到店服務已於 2026/7/1 停止（P03），<b>新訂單不能選</b>（ShippingInfoValidator 會擋）。
     * 這個值不能刪：舊訂單可能有，資料庫的 CHECK 也還允許
     */
    OK
}
