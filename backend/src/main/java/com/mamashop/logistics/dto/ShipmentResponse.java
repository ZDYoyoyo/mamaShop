package com.mamashop.logistics.dto;

import com.mamashop.logistics.entity.Shipment;
import java.time.Instant;

/**
 * 後台訂單頁顯示的物流單（P03）。
 *
 * @param logisticsId     綠界物流交易編號（到綠界後台查詢用）
 * @param paymentNo       寄件代碼（到超商寄件、列印寄件單用）
 * @param validationNo    驗證碼（只有 7-ELEVEN）
 * @param statusCode      最新的物流狀態代碼
 * @param statusMessage   最新的物流狀態說明
 */
public record ShipmentResponse(String logisticsId, String logisticsSubType, String paymentNo, String validationNo,
                               int statusCode, String statusMessage, Instant statusUpdatedAt, Instant createdAt) {

    public static ShipmentResponse from(Shipment s) {
        return new ShipmentResponse(s.getLogisticsId(), s.getLogisticsSubType(), s.getCvsPaymentNo(),
                s.getCvsValidationNo(), s.getStatusCode(), s.getStatusMessage(), s.getStatusUpdatedAt(), s.getCreatedAt());
    }
}
