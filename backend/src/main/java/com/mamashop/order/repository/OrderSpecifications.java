package com.mamashop.order.repository;

import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.shipping.entity.ShippingMethodCode;
import org.springframework.data.jpa.domain.Specification;

/** 後台訂單列表的篩選條件（都是選填，依參數有沒有值組合） */
public final class OrderSpecifications {

    private OrderSpecifications() {
    }

    public static Specification<Order> hasStatus(OrderStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Order> hasShippingMethod(ShippingMethodCode shippingMethod) {
        return (root, query, cb) -> cb.equal(root.get("shippingMethod"), shippingMethod);
    }
}
