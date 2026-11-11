package com.mamashop.shipping.dto;

import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;

public record AdminShippingMethodResponse(
        Long id, ShippingMethodCode code, String name, BigDecimal fee, boolean enabled, int sortOrder) {

    public static AdminShippingMethodResponse from(ShippingMethod m) {
        return new AdminShippingMethodResponse(
                m.getId(), m.getCode(), m.getName(), m.getFee(), m.isEnabled(), m.getSortOrder());
    }
}
