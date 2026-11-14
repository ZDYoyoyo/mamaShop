package com.mamashop.logistics.repository;

import com.mamashop.logistics.entity.Shipment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    long countByOrderId(Long orderId);

    /** 這張訂單最新的物流單 */
    Optional<Shipment> findFirstByOrderIdOrderByIdDesc(Long orderId);

    Optional<Shipment> findByLogisticsId(String logisticsId);

    /**
     * 只查訂單編號（不載入 entity）。狀態通知要先用它鎖住訂單，再讀物流單（先鎖、再讀，原因見 P01 的 PaymentService）
     */
    @Query("SELECT s.order.orderNo FROM Shipment s WHERE s.logisticsId = :logisticsId")
    Optional<String> findOrderNoByLogisticsId(@Param("logisticsId") String logisticsId);
}
