package com.mamashop.logistics.dto;

/** 結帳頁要知道的物流設定：超商取貨是用電子地圖選門市，還是手動填寫 */
public record LogisticsOptionsResponse(boolean cvsMapEnabled) {
}
