package com.mamashop.order.event;

import com.mamashop.shipping.entity.ShippingMethodCode;

/**
 * 訂單已送達（M02）。逾時自動處理（timeout 模組）聽這個事件，排定「N 天後自動完成」。
 *
 * <p>和 {@link OrderEvent} 分開：OrderEvent 是「要通知顧客」的事件（M01 會寄信），
 * 送達目前不寄信，混在一起的話通知模組要多一個「不處理」的分支。
 */
public record OrderDeliveredEvent(String orderNo, ShippingMethodCode shippingMethod) {
}
