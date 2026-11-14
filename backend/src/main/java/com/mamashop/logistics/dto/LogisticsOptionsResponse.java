package com.mamashop.logistics.dto;

/**
 * 物流設定。
 *
 * @param cvsMapEnabled   結帳頁：超商門市用電子地圖選（P02），還是手動填寫
 * @param shipmentEnabled 後台：超商取貨的訂單可以「建立物流單並出貨」（P03），還是手動填物流單號
 */
public record LogisticsOptionsResponse(boolean cvsMapEnabled, boolean shipmentEnabled) {
}
