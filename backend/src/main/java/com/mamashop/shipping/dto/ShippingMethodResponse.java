package com.mamashop.shipping.dto;

import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;

/** 前台結帳頁顯示的運送方式與運費 */
public record ShippingMethodResponse(ShippingMethodCode code, String name, BigDecimal fee) {

    public static ShippingMethodResponse from(ShippingMethod method) {
        return new ShippingMethodResponse(method.getCode(), method.getName(), method.getFee());
    }
}
