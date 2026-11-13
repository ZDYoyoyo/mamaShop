package com.mamashop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mamashop.common.exception.ErrorCode;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.OrderStatusHistory;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.event.OrderEvent;
import com.mamashop.order.event.OrderEventType;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.order.repository.OrderStatusHistoryRepository;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/** AdminOrderService 單元測試：版本號比對、付款狀態、退回是否歸還庫存。 */
@ExtendWith(MockitoExtension.class)
class AdminOrderServiceTest {

    private static final Long ADMIN_ID = 99L;

    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderStockService orderStockService;
    @Mock private ApplicationEventPublisher eventPublisher;

    @InjectMocks private AdminOrderService adminOrderService;

    private User admin;

    @BeforeEach
    void setUp() {
        admin = new User();
        admin.setName("媽媽");
        admin.setRole(Role.ADMIN);
    }

    @Test
    void staleVersion_isRejected_beforeAnyChange() {
        Order order = order(OrderStatus.PROCESSING, 4);
        when(orderRepository.findByOrderNo("M1")).thenReturn(Optional.of(order));

        // 畫面上是版本 3，資料庫已經是 4
        assertThatThrownBy(() -> adminOrderService.cancel("M1", 3, ADMIN_ID, null))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderRepository, never()).saveAndFlush(any());
        verifyNoInteractions(orderStockService, historyRepository);
    }

    @Test
    void complete_marksOrderAsPaid() {
        Order order = order(OrderStatus.DELIVERED, 3);
        stubAction(order);

        AdminOrderDetailResponse result = adminOrderService.complete("M1", 3, ADMIN_ID, null);

        assertThat(result.order().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(result.order().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        assertThat(result.order().paidAt()).isNotNull();
        assertThat(result.order().completedAt()).isNotNull();
    }

    @Test
    void cancel_restoresStock_andRecordsAdminAsOperator() {
        Order order = order(OrderStatus.PROCESSING, 1);
        stubAction(order);

        adminOrderService.cancel("M1", 1, ADMIN_ID, "顧客來電取消");

        verify(orderStockService).restore(order);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getOperator()).isSameAs(admin);
        assertThat(history.getValue().getNote()).isEqualTo("顧客來電取消");
        // 取消目前不寄信（M01 只通知下單、確認、出貨）
        verifyNoInteractions(eventPublisher);
    }

    @Test
    void ship_setsTrackingNo() {
        Order order = order(OrderStatus.PROCESSING, 1);
        stubAction(order);

        AdminOrderDetailResponse result = adminOrderService.ship("M1", 1, "9988776655", ADMIN_ID, null);

        assertThat(result.order().trackingNo()).isEqualTo("9988776655");
        assertThat(result.order().shippedAt()).isNotNull();
        verifyNoInteractions(orderStockService);
        // 通知顧客已出貨（M01）
        verify(eventPublisher).publishEvent(new OrderEvent("M1", OrderEventType.SHIPPED));
    }

    @Test
    void confirm_publishesEventForNotification() {
        Order order = order(OrderStatus.PENDING_CONFIRM, 0);
        stubAction(order);

        adminOrderService.confirm("M1", 0, ADMIN_ID, null);

        verify(eventPublisher).publishEvent(new OrderEvent("M1", OrderEventType.CONFIRMED));
    }

    @Test
    void returned_withoutRestock_doesNotTouchStock() {
        Order order = order(OrderStatus.SHIPPED, 2);
        stubAction(order);

        adminOrderService.markReturned("M1", 2, false, ADMIN_ID, "包裝破損");

        verifyNoInteractions(orderStockService);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getNote()).isEqualTo("未歸還庫存；包裝破損");
    }

    @Test
    void returned_withRestock_restoresStock() {
        Order order = order(OrderStatus.DELIVERED, 3);
        stubAction(order);

        adminOrderService.markReturned("M1", 3, true, ADMIN_ID, null);

        verify(orderStockService).restore(order);
    }

    @Test
    void invalidTransition_isRejected_withoutSaving() {
        Order order = order(OrderStatus.PENDING_CONFIRM, 0);
        when(orderRepository.findByOrderNo("M1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> adminOrderService.deliver("M1", 0, ADMIN_ID, null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    // ===== helpers =====

    private void stubAction(Order order) {
        when(orderRepository.findByOrderNo("M1")).thenReturn(Optional.of(order));
        when(userRepository.getReferenceById(ADMIN_ID)).thenReturn(admin);
        when(historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(any())).thenReturn(List.of());
    }

    private static Order order(OrderStatus status, long version) {
        Order order = new Order();
        order.setOrderNo("M1");
        order.setStatus(status);
        order.setUser(new User());
        ReflectionTestUtils.setField(order, "version", version);
        return order;
    }
}
