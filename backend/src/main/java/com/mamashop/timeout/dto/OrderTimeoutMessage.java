package com.mamashop.timeout.dto;

/** 延遲任務的訊息內容：只放訂單編號，處理時再查最新的訂單 */
public record OrderTimeoutMessage(String orderNo) {
}
