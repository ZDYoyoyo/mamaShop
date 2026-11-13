package com.mamashop.dashboard.service;

import com.mamashop.dashboard.dto.AdminDashboardResponse;
import com.mamashop.dashboard.dto.AdminDashboardResponse.LowStockProduct;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 後台首頁概覽：幾個數字＋低庫存商品。
 *
 * <p>每個數字都是一個 COUNT 查詢（不載入資料），所以很便宜：
 * 訂單量再大，資料庫回傳的也只是一個數字。商品與訂單量對這個專案來說都很小，
 * 先不做快取；R 系列（Redis）壓測時如果這裡變熱點再處理。
 *
 * <p>「今天」用台灣時間：伺服器（Docker）的時區是 UTC，直接用 LocalDate.now() 會在早上 8 點才換日。
 * 這裡算出「台灣今天 00:00」對應的 Instant，再和 created_at（TIMESTAMPTZ）比較。
 */
@Service
@Transactional(readOnly = true)
public class AdminDashboardService {

    static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    /** 和前台「僅剩 N 件」的門檻一致（frontend/src/utils/format.js 的 LOW_STOCK_THRESHOLD） */
    static final int LOW_STOCK_THRESHOLD = 5;
    static final int RETURNED_DAYS = 30;

    private final OrderRepository orderRepository;
    private final ProductRepository productRepository;
    private final Clock clock;

    @Autowired
    public AdminDashboardService(OrderRepository orderRepository, ProductRepository productRepository) {
        this(orderRepository, productRepository, Clock.systemUTC());
    }

    /** 測試用：固定時間，驗證「今天」的換日點 */
    AdminDashboardService(OrderRepository orderRepository, ProductRepository productRepository, Clock clock) {
        this.orderRepository = orderRepository;
        this.productRepository = productRepository;
        this.clock = clock;
    }

    public AdminDashboardResponse get() {
        Instant now = clock.instant();
        Instant startOfToday = LocalDate.ofInstant(now, TAIPEI).atStartOfDay(TAIPEI).toInstant();

        return new AdminDashboardResponse(
                orderRepository.countByCreatedAtGreaterThanEqual(startOfToday),
                orderRepository.countByStatus(OrderStatus.PENDING_CONFIRM),
                orderRepository.countByStatus(OrderStatus.PROCESSING),
                orderRepository.countByStatusAndShippingMethod(OrderStatus.DELIVERED, ShippingMethodCode.CVS_PICKUP),
                orderRepository.countByStatusAndReturnedAtGreaterThanEqual(
                        OrderStatus.RETURNED, now.minus(Duration.ofDays(RETURNED_DAYS))),
                RETURNED_DAYS,
                productRepository.countByStatusAndStockLessThanEqual(ProductStatus.ON_SALE, LOW_STOCK_THRESHOLD),
                LOW_STOCK_THRESHOLD,
                productRepository
                        .findTop20ByStatusAndStockLessThanEqualOrderByStockAscIdAsc(ProductStatus.ON_SALE, LOW_STOCK_THRESHOLD)
                        .stream().map(LowStockProduct::from).toList());
    }
}
