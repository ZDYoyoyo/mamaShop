package com.mamashop.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.mamashop.category.entity.Category;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.OrderStatusHistory;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.event.OrderAwaitingPaymentEvent;
import com.mamashop.order.event.OrderEvent;
import com.mamashop.order.event.OrderEventType;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.order.repository.OrderStatusHistoryRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.shipping.repository.ShippingMethodRepository;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.SortedMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

/**
 * OrderService 單元測試：Repository 全部用 Mockito 假造，不啟動 Spring、不連資料庫，一個測試幾毫秒。
 *
 * <p>和整合測試的分工：
 * <ul>
 *   <li>單元測試（這裡）：Service 的「邏輯」——合併數量、排序、金額計算、狀態判斷、呼叫了誰／沒呼叫誰</li>
 *   <li>整合測試（CreateOrderIntegrationTest 等）：真的 SQL、交易回滾、資料庫限制、併發</li>
 * </ul>
 * 交易回滾、條件更新這類行為只有真的資料庫才測得出來，所以兩種都需要。
 *
 * <p>MockitoExtension 預設是 strict stubs：設定了卻沒被用到的 stub 會讓測試失敗，避免測試寫了多餘的假設。
 */
@ExtendWith(MockitoExtension.class)
class OrderServiceTest {

    private static final Long USER_ID = 7L;

    @Mock private ProductRepository productRepository;
    @Mock private ShippingMethodRepository shippingMethodRepository;
    @Mock private OrderRepository orderRepository;
    @Mock private OrderStatusHistoryRepository historyRepository;
    @Mock private UserRepository userRepository;
    @Mock private OrderNumberGenerator orderNumberGenerator;
    @Mock private OrderStockService orderStockService;
    @Mock private ApplicationEventPublisher eventPublisher;

    /** @InjectMocks：用上面的 mock 呼叫 OrderService 的建構子 */
    @InjectMocks private OrderService orderService;

    private User customer;

    @BeforeEach
    void setUp() {
        customer = new User();
        ReflectionTestUtils.setField(customer, "id", USER_ID);
    }

    // ===== 建立訂單 =====

    @Test
    void createOrder_mergesDuplicateItems_andDeductsInProductIdOrder() {
        stubHappyPath(product(1L, "帆布包", "100.00"), product(5L, "零錢包", "50.00"));

        // 購物車順序是 5、1、5：應合併成 5 → 3 件、1 → 2 件，並依 id 排成 1、5
        orderService.createOrder(USER_ID, homeDelivery(item(5L, 1), item(1L, 2), item(5L, 2)));

        @SuppressWarnings("unchecked")
        ArgumentCaptor<SortedMap<Long, Integer>> captor = ArgumentCaptor.forClass(SortedMap.class);
        verify(orderStockService).deduct(captor.capture());
        assertThat(captor.getValue().keySet()).containsExactly(1L, 5L);
        assertThat(captor.getValue()).containsEntry(1L, 2).containsEntry(5L, 3);
    }

    @Test
    void createOrder_calculatesAmountsWithBigDecimalPrecision() {
        // 0.10 × 3 用 double 會變成 0.30000000000000004；BigDecimal 是精確的 0.30
        stubHappyPath(product(1L, "布料", "0.10"));

        OrderDetailResponse result = orderService.createOrder(USER_ID, homeDelivery(item(1L, 3)));

        assertThat(result.items().get(0).subtotal()).isEqualByComparingTo("0.30");
        assertThat(result.itemsAmount()).isEqualByComparingTo("0.30");
        assertThat(result.shippingFee()).isEqualByComparingTo("100.00");
        assertThat(result.totalAmount()).isEqualByComparingTo("100.30");
    }

    @Test
    void createOrder_snapshotsProductAndSetsInitialStatus() {
        stubHappyPath(product(1L, "帆布包", "890.00"));

        OrderDetailResponse result = orderService.createOrder(USER_ID, homeDelivery(item(1L, 1)));

        assertThat(result.orderNo()).isEqualTo("M2611120000000001");
        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(result.paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
        assertThat(result.items().get(0).productName()).isEqualTo("帆布包");
        assertThat(result.items().get(0).unitPrice()).isEqualByComparingTo("890.00");

        // 第一筆狀態紀錄：NULL → PENDING_CONFIRM
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isNull();
        assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(history.getValue().getOperator()).isSameAs(customer);

        // 發出「訂單成立」事件（M01）；真正寄信要等交易 commit 之後
        verify(eventPublisher).publishEvent(new OrderEvent("M2611120000000001", OrderEventType.CREATED));
    }

    @Test
    void createOrder_online_startsAsPendingPayment_andSchedulesTimeout() {
        ReflectionTestUtils.setField(orderService, "onlinePaymentEnabled", true);
        stubHappyPath(product(1L, "帆布包", "890.00"));

        OrderDetailResponse result = orderService.createOrder(USER_ID, online(homeDelivery(item(1L, 1))));

        assertThat(result.status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(result.paymentMethod()).isEqualTo(PaymentMethod.ONLINE);
        // 「訂單成立」通知＋排定逾時未付款自動取消（P01）
        verify(eventPublisher).publishEvent(new OrderEvent("M2611120000000001", OrderEventType.CREATED));
        verify(eventPublisher).publishEvent(new OrderAwaitingPaymentEvent("M2611120000000001"));
    }

    @Test
    void createOrder_online_whenDisabled_isRejectedBeforeTouchingStock() {
        assertThatThrownBy(() -> orderService.createOrder(USER_ID, online(homeDelivery(item(1L, 1)))))
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_METHOD_UNAVAILABLE);
        verifyNoInteractions(orderStockService);
    }

    @Test
    void createOrder_online_withCents_isRejected() {
        // 綠界只收整數金額
        ReflectionTestUtils.setField(orderService, "onlinePaymentEnabled", true);
        stubShippingAndProducts(product(1L, "零錢包", "99.50"));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, online(homeDelivery(item(1L, 1)))))
                .extracting("errorCode").isEqualTo(ErrorCode.ONLINE_PAYMENT_AMOUNT_INVALID);
    }

    private static CreateOrderRequest online(CreateOrderRequest r) {
        return new CreateOrderRequest(r.items(), r.shippingMethod(), r.recipientName(), r.recipientPhone(),
                r.shippingAddress(), r.cvsBrand(), r.cvsStoreCode(), r.cvsStoreName(), r.note(), PaymentMethod.ONLINE);
    }

    @Test
    void createOrder_homeDelivery_dropsCvsFields() {
        stubHappyPath(product(1L, "帆布包", "890.00"));
        CreateOrderRequest request = new CreateOrderRequest(List.of(item(1L, 1)), ShippingMethodCode.HOME_DELIVERY,
                "王小明", "0912345678", "台北市", CvsBrand.SEVEN, "123456", "不該被存", null);

        OrderDetailResponse result = orderService.createOrder(USER_ID, request);

        assertThat(result.shippingAddress()).isEqualTo("台北市");
        assertThat(result.cvsBrand()).isNull();
        assertThat(result.cvsStoreName()).isNull();
    }

    @Test
    void createOrder_disabledShippingMethod_failsBeforeTouchingStock() {
        ShippingMethod disabled = shippingMethod("100.00");
        disabled.setEnabled(false);
        when(shippingMethodRepository.findByCode(ShippingMethodCode.HOME_DELIVERY)).thenReturn(Optional.of(disabled));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, homeDelivery(item(1L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.SHIPPING_METHOD_UNAVAILABLE);
        verifyNoInteractions(orderStockService, orderRepository);
    }

    @Test
    void createOrder_mergedQuantityOver99_failsBeforeAnyDatabaseAccess() {
        assertThatThrownBy(() -> orderService.createOrder(USER_ID, homeDelivery(item(1L, 60), item(1L, 40))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.VALIDATION_FAILED);
        verifyNoInteractions(shippingMethodRepository, orderStockService, orderRepository);
    }

    @Test
    void createOrder_stockFailure_propagates_andOrderIsNeverSaved() {
        when(shippingMethodRepository.findByCode(ShippingMethodCode.HOME_DELIVERY))
                .thenReturn(Optional.of(shippingMethod("100.00")));
        doThrow(new BusinessException(ErrorCode.INSUFFICIENT_STOCK)).when(orderStockService).deduct(any());

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, homeDelivery(item(1L, 1))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.INSUFFICIENT_STOCK);
        verifyNoInteractions(orderRepository, historyRepository, eventPublisher);
    }

    @Test
    void createOrder_amountOverNumericLimit_isRejected() {
        stubShippingAndProducts(product(1L, "超貴商品", "99999999.00"));

        assertThatThrownBy(() -> orderService.createOrder(USER_ID, homeDelivery(item(1L, 2))))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_AMOUNT_TOO_LARGE);
        verify(orderRepository, never()).save(any());
    }

    // ===== 取消訂單 =====

    @Test
    void cancel_pendingOrder_restoresStock_andRecordsHistory() {
        Order order = existingOrder(OrderStatus.PENDING_CONFIRM);
        when(orderRepository.findByOrderNoAndUserId("M1", USER_ID)).thenReturn(Optional.of(order));
        when(userRepository.getReferenceById(USER_ID)).thenReturn(customer);

        OrderDetailResponse result = orderService.cancelMyOrder(USER_ID, "M1");

        assertThat(result.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(result.cancelledAt()).isNotNull();
        verify(orderRepository).saveAndFlush(order);
        verify(orderStockService).restore(order);
        ArgumentCaptor<OrderStatusHistory> history = ArgumentCaptor.forClass(OrderStatusHistory.class);
        verify(historyRepository).save(history.capture());
        assertThat(history.getValue().getFromStatus()).isEqualTo(OrderStatus.PENDING_CONFIRM);
        assertThat(history.getValue().getToStatus()).isEqualTo(OrderStatus.CANCELLED);
    }

    @Test
    void cancel_afterConfirmation_isRejected_andStockUntouched() {
        when(orderRepository.findByOrderNoAndUserId("M1", USER_ID))
                .thenReturn(Optional.of(existingOrder(OrderStatus.PROCESSING)));

        assertThatThrownBy(() -> orderService.cancelMyOrder(USER_ID, "M1"))
                .extracting("errorCode").isEqualTo(ErrorCode.ORDER_CANNOT_CANCEL);
        verifyNoInteractions(orderStockService);
        verify(orderRepository, never()).saveAndFlush(any());
    }

    @Test
    void cancel_alreadyCancelled_isRejected() {
        when(orderRepository.findByOrderNoAndUserId("M1", USER_ID))
                .thenReturn(Optional.of(existingOrder(OrderStatus.CANCELLED)));

        assertThatThrownBy(() -> orderService.cancelMyOrder(USER_ID, "M1"))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        verifyNoInteractions(orderStockService);
    }

    @Test
    void cancel_someoneElsesOrder_isNotFound() {
        // Repository 以 orderNo + userId 查詢，別人的訂單查不到
        when(orderRepository.findByOrderNoAndUserId(anyString(), any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelMyOrder(USER_ID, "M-OTHERS"))
                .isInstanceOf(NotFoundException.class);
        verifyNoInteractions(orderStockService);
    }

    // ===== helpers =====

    private void stubHappyPath(Product... products) {
        stubShippingAndProducts(products);
        when(userRepository.getReferenceById(USER_ID)).thenReturn(customer);
        when(orderNumberGenerator.generate()).thenReturn("M2611120000000001");
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private void stubShippingAndProducts(Product... products) {
        when(shippingMethodRepository.findByCode(ShippingMethodCode.HOME_DELIVERY))
                .thenReturn(Optional.of(shippingMethod("100.00")));
        when(productRepository.findAllById(any())).thenReturn(List.of(products));
    }

    private static Product product(Long id, String name, String price) {
        Product p = new Product();
        ReflectionTestUtils.setField(p, "id", id); // id 沒有 setter（由資料庫產生），測試用反射設定
        p.setCategory(new Category());
        p.setName(name);
        p.setPrice(new BigDecimal(price));
        p.setStatus(ProductStatus.ON_SALE);
        return p;
    }

    private static ShippingMethod shippingMethod(String fee) {
        ShippingMethod m = new ShippingMethod();
        m.setCode(ShippingMethodCode.HOME_DELIVERY);
        m.setFee(new BigDecimal(fee));
        m.setEnabled(true);
        return m;
    }

    private Order existingOrder(OrderStatus status) {
        Order order = new Order();
        order.setOrderNo("M1");
        order.setUser(customer);
        order.setStatus(status);
        OrderItem item = new OrderItem();
        item.setProduct(product(1L, "帆布包", "100.00"));
        item.setProductName("帆布包");
        item.setUnitPrice(new BigDecimal("100.00"));
        item.setQuantity(2);
        item.setSubtotal(new BigDecimal("200.00"));
        order.addItem(item);
        return order;
    }

    private static CreateOrderRequest.Item item(Long productId, int quantity) {
        return new CreateOrderRequest.Item(productId, quantity);
    }

    private static CreateOrderRequest homeDelivery(CreateOrderRequest.Item... items) {
        return new CreateOrderRequest(List.of(items), ShippingMethodCode.HOME_DELIVERY,
                "王小明", "0912345678", "台北市", null, null, null, null);
    }
}
