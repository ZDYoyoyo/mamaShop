package com.mamashop.payment.repository;

import com.mamashop.payment.entity.Payment;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentRepository extends JpaRepository<Payment, Long> {

    long countByOrderId(Long orderId);

    /**
     * 只查訂單編號（不載入任何 entity）。付款通知要先用它鎖住訂單，原因見 PaymentService#handleEcpayNotification
     */
    @Query("SELECT p.order.orderNo FROM Payment p WHERE p.merchantTradeNo = :merchantTradeNo")
    Optional<String> findOrderNoByMerchantTradeNo(@Param("merchantTradeNo") String merchantTradeNo);

    Optional<Payment> findByMerchantTradeNo(String merchantTradeNo);
}
