package com.mamashop.order.repository;

import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.shipping.entity.ShippingMethodCode;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/** 訂單明細（OrderItem）透過 Order 的 cascade 一起存，所以不另外建 Repository。 */
public interface OrderRepository extends JpaRepository<Order, Long>, JpaSpecificationExecutor<Order> {

    /**
     * 我的訂單（分頁）。
     * 列表需要明細摘要，但「一對多 JOIN FETCH + 分頁」會讓 Hibernate 把所有資料撈回記憶體再分頁，
     * 所以這裡不 JOIN，改用 hibernate.default_batch_fetch_size 一次用 IN 查回這一頁所有訂單的明細。
     */
    Page<Order> findByUserId(Long userId, Pageable pageable);

    /**
     * 查詢條件同時帶 orderNo 與 userId：別人的訂單直接查不到（回 404），
     * 不用先查出來再比對擁有者，也就不會有「忘記比對」的風險。
     */
    @EntityGraph(attributePaths = "items")
    Optional<Order> findByOrderNoAndUserId(String orderNo, Long userId);

    /** 後台：不限會員。一起載入明細與下單會員 */
    @EntityGraph(attributePaths = {"items", "user"})
    Optional<Order> findByOrderNo(String orderNo);

    /**
     * 讀取並鎖住訂單（SELECT ... FOR UPDATE），交易結束才放開（P01）。
     *
     * <p>用在「系統」同時可能從兩個地方改同一張訂單的情況：金流通知「付款成功」和「逾時未付款自動取消」
     * 剛好同時發生。用 @Version 的話，後到的那個會失敗、要靠重試；鎖住的話後到的那個會<b>等</b>前一個做完，
     * 再看到最新的狀態（已付款 → 不取消；已取消 → 記錄要退款），兩邊都不用失敗重來。
     * 管理員畫面的操作還是用版本號比對（要知道「畫面上看到的是不是最新的」，鎖解決不了這個）。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT o FROM Order o WHERE o.orderNo = :orderNo")
    Optional<Order> findForUpdateByOrderNo(@Param("orderNo") String orderNo);

    /**
     * 後台訂單列表：一起 JOIN 下單會員（多對一，不影響分頁），顯示顧客名字時不會 N+1。
     * 明細仍由 default_batch_fetch_size 批次載入。
     */
    @Override
    @EntityGraph(attributePaths = "user")
    Page<Order> findAll(Specification<Order> spec, Pageable pageable);

    // ===== 後台概覽（A05）：只算數量，不載入訂單 =====

    /** 某個時間點之後建立的訂單數（今日訂單） */
    long countByCreatedAtGreaterThanEqual(Instant from);

    long countByStatus(OrderStatus status);

    /** 例如：超商取貨、已送達（到店未取件） */
    long countByStatusAndShippingMethod(OrderStatus status, ShippingMethodCode shippingMethod);

    /** 例如：最近 30 天退回 */
    long countByStatusAndReturnedAtGreaterThanEqual(OrderStatus status, Instant from);
}
