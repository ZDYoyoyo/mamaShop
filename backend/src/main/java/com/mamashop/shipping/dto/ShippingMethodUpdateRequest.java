package com.mamashop.shipping.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 修改運送方式。code（HOME_DELIVERY／CVS_PICKUP）不能改：程式依 code 判斷要驗證地址還是門市。
 * 也不提供新增、刪除：運送方式種類由程式定義，新增種類需要改程式。
 */
public record ShippingMethodUpdateRequest(
        @NotBlank @Size(max = 50) String name,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal fee,
        @NotNull Boolean enabled,
        @NotNull @Min(0) Integer sortOrder) {

    public ShippingMethodUpdateRequest {
        name = name == null ? null : name.trim();
    }
}
