package com.mamashop.order.repository;

import com.mamashop.order.entity.OrderStatusHistory;
import java.util.List;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatusHistoryRepository extends JpaRepository<OrderStatusHistory, Long> {

    /** 某張訂單的狀態紀錄（舊到新），一起載入操作者以顯示名字 */
    @EntityGraph(attributePaths = "operator")
    List<OrderStatusHistory> findByOrderIdOrderByCreatedAtAscIdAsc(Long orderId);
}
