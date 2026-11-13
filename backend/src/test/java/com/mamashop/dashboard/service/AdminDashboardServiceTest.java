package com.mamashop.dashboard.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** 「今天」的換日點：伺服器是 UTC，但要用台灣時間的 00:00 換日 */
class AdminDashboardServiceTest {

    private OrderRepository orderRepository;
    private ProductRepository productRepository;

    @BeforeEach
    void setUp() {
        orderRepository = mock(OrderRepository.class);
        productRepository = mock(ProductRepository.class);
        when(productRepository.findTop20ByStatusAndStockLessThanEqualOrderByStockAscIdAsc(any(), anyInt()))
                .thenReturn(List.of());
    }

    private AdminDashboardService at(String utc) {
        return new AdminDashboardService(orderRepository, productRepository,
                Clock.fixed(Instant.parse(utc), ZoneOffset.UTC));
    }

    @Test
    void todayStartsAtMidnightTaipei_evenWhenUtcIsStillYesterday() {
        // UTC 11/13 16:30 = 台灣 11/14 00:30 → 今天從台灣 11/14 00:00（UTC 11/13 16:00）開始
        at("2026-11-13T16:30:00Z").get();

        verify(orderRepository).countByCreatedAtGreaterThanEqual(Instant.parse("2026-11-13T16:00:00Z"));
    }

    @Test
    void justBeforeMidnightTaipei_isStillTheSameDay() {
        // UTC 11/13 15:59 = 台灣 11/13 23:59 → 今天從台灣 11/13 00:00（UTC 11/12 16:00）開始
        at("2026-11-13T15:59:00Z").get();

        verify(orderRepository).countByCreatedAtGreaterThanEqual(Instant.parse("2026-11-12T16:00:00Z"));
    }

    @Test
    void countsEachCategory() {
        when(orderRepository.countByStatus(OrderStatus.PENDING_CONFIRM)).thenReturn(3L);
        when(orderRepository.countByStatus(OrderStatus.PROCESSING)).thenReturn(2L);
        when(orderRepository.countByStatusAndShippingMethod(OrderStatus.DELIVERED, ShippingMethodCode.CVS_PICKUP))
                .thenReturn(1L);
        when(orderRepository.countByStatusAndReturnedAtGreaterThanEqual(
                eq(OrderStatus.RETURNED), eq(Instant.parse("2026-10-14T04:00:00Z")))).thenReturn(4L);
        Product product = new Product();
        product.setName("帆布托特包");
        product.setStock(0);
        when(productRepository.countByStatusAndStockLessThanEqual(ProductStatus.ON_SALE, 5)).thenReturn(1L);
        when(productRepository.findTop20ByStatusAndStockLessThanEqualOrderByStockAscIdAsc(ProductStatus.ON_SALE, 5))
                .thenReturn(List.of(product));

        var result = at("2026-11-13T04:00:00Z").get();

        assertThat(result.pendingConfirm()).isEqualTo(3);
        assertThat(result.awaitingShipment()).isEqualTo(2);
        assertThat(result.awaitingPickup()).isEqualTo(1);
        assertThat(result.returnedRecently()).isEqualTo(4); // 30 天前（10/14）之後退回的
        assertThat(result.lowStockCount()).isEqualTo(1);
        assertThat(result.lowStockProducts()).extracting("name", "stock").containsExactly(org.assertj.core.groups.Tuple.tuple("帆布托特包", 0));
    }
}
