package com.mamashop.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 出貨：必填物流單號（顧客可用來查件） */
public record ShipOrderRequest(
        @NotNull Long version,
        @NotBlank @Size(max = 50) String trackingNo,
        @Size(max = 255) String note) {

    public ShipOrderRequest {
        trackingNo = trackingNo == null ? null : trackingNo.trim();
    }
}
