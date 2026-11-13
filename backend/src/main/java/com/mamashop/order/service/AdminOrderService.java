package com.mamashop.order.service;

import static com.mamashop.order.repository.OrderSpecifications.hasShippingMethod;
import static com.mamashop.order.repository.OrderSpecifications.hasStatus;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.common.response.PageResponse;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.AdminOrderSummaryResponse;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.OrderStatusHistory;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.event.OrderEvent;
import com.mamashop.order.event.OrderEventType;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.order.repository.OrderStatusHistoryRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.repository.UserRepository;
import java.time.Instant;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 後台訂單管理（B10）。
 *
 * <h2>每個操作都做三件事</h2>
 * <ol>
 *   <li><b>比對版本號</b>：管理員畫面上的版本 ≠ 資料庫的版本 → 409（有人在這段時間改過訂單）</li>
 *   <li><b>經過狀態機</b>：{@link Order#transitionTo} 檢查是否允許，並記錄時間</li>
 *   <li><b>寫入狀態紀錄</b>：記下是哪位管理員、什麼時候、從什麼狀態改成什麼狀態</li>
 * </ol>
 *
 * <h2>兩層防護：版本號比對 vs. @Version</h2>
 * <ul>
 *   <li><b>版本號比對（跨請求）</b>：管理員 A 打開訂單（版本 3），B 確認了訂單（版本 4），
 *       A 再按「取消」。伺服器讀到的是最新資料，狀態機也允許「備貨中 → 已取消」，
 *       如果不比對版本，A 會在不知情的情況下取消 B 剛確認的訂單。</li>
 *   <li><b>@Version（同一瞬間）</b>：A、B 同時送出，兩個交易都讀到版本 3、都通過比對，
 *       但寫入時 Hibernate 用 WHERE version = 3 更新，後寫入的那個更新 0 筆 → 失敗回滾。</li>
 * </ul>
 */
@Service
public class AdminOrderService {

    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final OrderRepository orderRepository;
    private final OrderStatusHistoryRepository historyRepository;
    private final UserRepository userRepository;
    private final OrderStockService orderStockService;
    private final ApplicationEventPublisher eventPublisher;

    public AdminOrderService(OrderRepository orderRepository, OrderStatusHistoryRepository historyRepository,
                             UserRepository userRepository, OrderStockService orderStockService,
                             ApplicationEventPublisher eventPublisher) {
        this.orderRepository = orderRepository;
        this.historyRepository = historyRepository;
        this.userRepository = userRepository;
        this.orderStockService = orderStockService;
        this.eventPublisher = eventPublisher;
    }

    // ===== 查詢 =====

    @Transactional(readOnly = true)
    public PageResponse<AdminOrderSummaryResponse> search(OrderStatus status, ShippingMethodCode shippingMethod,
                                                          int page, int size) {
        Specification<Order> spec = Specification.unrestricted();
        if (status != null) {
            spec = spec.and(hasStatus(status));
        }
        if (shippingMethod != null) {
            spec = spec.and(hasShippingMethod(shippingMethod));
        }
        return PageResponse.of(
                orderRepository.findAll(spec, PageRequest.of(page - 1, size, NEWEST_FIRST)),
                AdminOrderSummaryResponse::from);
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse get(String orderNo) {
        return toDetail(findOrThrow(orderNo));
    }

    // ===== 操作 =====

    /** 確認訂單（待確認／待付款 → 備貨中）。例如家人電話確認過顧客 */
    @Transactional
    public AdminOrderDetailResponse confirm(String orderNo, long version, Long adminId, String note) {
        Order order = loadForUpdate(orderNo, version);
        changeStatus(order, OrderStatus.PROCESSING, adminId, note);
        // 通知顧客（M01），交易 commit 後才寄
        eventPublisher.publishEvent(new OrderEvent(orderNo, OrderEventType.CONFIRMED));
        return toDetail(order);
    }

    /** 出貨（備貨中 → 已出貨），填物流單號 */
    @Transactional
    public AdminOrderDetailResponse ship(String orderNo, long version, String trackingNo, Long adminId, String note) {
        Order order = loadForUpdate(orderNo, version);
        order.setTrackingNo(trackingNo);
        changeStatus(order, OrderStatus.SHIPPED, adminId, note);
        eventPublisher.publishEvent(new OrderEvent(orderNo, OrderEventType.SHIPPED));
        return toDetail(order);
    }

    /** 已送達／已到店（已出貨 → 已送達） */
    @Transactional
    public AdminOrderDetailResponse deliver(String orderNo, long version, Long adminId, String note) {
        Order order = loadForUpdate(orderNo, version);
        changeStatus(order, OrderStatus.DELIVERED, adminId, note);
        return toDetail(order);
    }

    /**
     * 完成（已送達 → 已完成）。貨到付款的錢在顧客簽收／取件時收到，所以同時標記為已付款。
     */
    @Transactional
    public AdminOrderDetailResponse complete(String orderNo, long version, Long adminId, String note) {
        Order order = loadForUpdate(orderNo, version);
        Instant now = Instant.now();
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(now);
        changeStatus(order, OrderStatus.COMPLETED, adminId, note);
        return toDetail(order);
    }

    /**
     * 管理員取消（出貨前都可以，由狀態機決定），歸還庫存。
     * 和顧客取消不同：顧客只能在「待確認」時取消。
     */
    @Transactional
    public AdminOrderDetailResponse cancel(String orderNo, long version, Long adminId, String note) {
        Order order = loadForUpdate(orderNo, version);
        changeStatus(order, OrderStatus.CANCELLED, adminId, note);
        orderStockService.restore(order);
        return toDetail(order);
    }

    /**
     * 退回（已出貨／已送達 → 已退回），例如超商未取件。
     *
     * @param restock 是否歸還庫存：商品可能損壞，由管理員決定
     */
    @Transactional
    public AdminOrderDetailResponse markReturned(String orderNo, long version, boolean restock,
                                                 Long adminId, String note) {
        Order order = loadForUpdate(orderNo, version);
        String fullNote = (restock ? "已歸還庫存" : "未歸還庫存") + (note == null || note.isBlank() ? "" : "；" + note);
        changeStatus(order, OrderStatus.RETURNED, adminId, fullNote);
        if (restock) {
            orderStockService.restore(order);
        }
        return toDetail(order);
    }

    // ===== helpers =====

    /** 讀出訂單並比對版本號：不同代表管理員看到的是舊資料 */
    private Order loadForUpdate(String orderNo, long expectedVersion) {
        Order order = findOrThrow(orderNo);
        if (order.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return order;
    }

    /**
     * 經過狀態機改狀態、立即寫入（@Version 檢查在這裡發生），再寫狀態紀錄。
     * 先 flush 訂單再做其他事（例如歸還庫存）：版本衝突的話在這裡就失敗。
     */
    private void changeStatus(Order order, OrderStatus target, Long adminId, String note) {
        OrderStatus from = order.transitionTo(target, Instant.now());
        orderRepository.saveAndFlush(order);
        historyRepository.save(new OrderStatusHistory(
                order, from, target, userRepository.getReferenceById(adminId), note));
    }

    private Order findOrThrow(String orderNo) {
        return orderRepository.findByOrderNo(orderNo).orElseThrow(() -> new NotFoundException("找不到訂單"));
    }

    private AdminOrderDetailResponse toDetail(Order order) {
        historyRepository.flush();
        return AdminOrderDetailResponse.from(order,
                historyRepository.findByOrderIdOrderByCreatedAtAscIdAsc(order.getId()));
    }
}
