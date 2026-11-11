package com.mamashop.order.repository;

import com.mamashop.order.entity.Order;
import org.springframework.data.jpa.repository.JpaRepository;

/** 訂單明細（OrderItem）透過 Order 的 cascade 一起存，所以不另外建 Repository。 */
public interface OrderRepository extends JpaRepository<Order, Long> {
}
