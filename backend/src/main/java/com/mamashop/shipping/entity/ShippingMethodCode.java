package com.mamashop.shipping.entity;

/** 運送方式代碼。shipping_methods.code 與 orders.shipping_method 共用。 */
public enum ShippingMethodCode {
    /** 宅配 */
    HOME_DELIVERY,
    /** 超商取貨 */
    CVS_PICKUP
}
