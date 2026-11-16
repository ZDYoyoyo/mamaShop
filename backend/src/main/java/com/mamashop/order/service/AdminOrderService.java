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
import com.mamashop.order.event.OrderDeliveredEvent;
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
 * 訂單狀態的所有「非顧客」操作：管理員在後台按的（B10），以及系統自動做的（M02、P01、P03）。
 *
 * <h2>這個類別有兩種呼叫者，防止互相覆蓋的方式不同</h2>
 * <ul>
 *   <li><b>管理員（後台畫面）</b>：confirm、ship、deliver、complete、cancel、markReturned。
 *       用<b>樂觀鎖</b>：比對畫面上的版本號＋@Version。衝突時回 409，請人重新整理後再決定</li>
 *   <li><b>系統（沒有人在看畫面）</b>：onlinePaymentReceived（金流通知，P01）、autoCancelUnpaid（逾時未付款，P01）、
 *       logisticsStatusChanged（物流通知，P03）、autoComplete（送達 N 天，M02）。
 *       用<b>悲觀鎖</b>（SELECT ... FOR UPDATE；autoComplete 例外，見該方法）：後到的等前一個做完，
 *       再依<b>最新狀態</b>決定要不要做。沒有人可以「看到 409 再按一次」，所以不能靠失敗來解決。
 *       狀態紀錄的操作者是 NULL（畫面顯示「系統」）</li>
 * </ul>
 *
 * <h2>管理員的每個操作都做三件事</h2>
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

    /**
     * 確認訂單（待確認 → 備貨中）。例如家人電話確認過顧客。
     *
     * <p><b>「待付款」（線上付款）的訂單不能手動確認</b>，只有金流通知付款成功才會進入備貨
     * （{@link #onlinePaymentReceived}）。狀態機本身允許「待付款 → 備貨中」（給系統用），所以要在這裡擋：
     * 手動確認的話，訂單會變成「備貨中＋未付款」，顧客不能再付款（只有待付款能付）、逾時也不會自動取消，
     * 超商取貨還會當成「已線上付款」不代收貨款 —— 等於沒收到錢就出貨。
     * 擋下之後可以確定：<b>線上付款的訂單只要過了待付款，就一定已經付款</b>。
     */
    @Transactional
    public AdminOrderDetailResponse confirm(String orderNo, long version, Long adminId, String note) {
        Order order = loadAndCheckVersion(orderNo, version);
        if (order.getStatus() == OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.INVALID_ORDER_STATUS,
                    "線上付款的訂單要等顧客付款，付款成功後會自動進入備貨；不想等可以取消訂單");
        }
        changeStatus(order, OrderStatus.PROCESSING, adminId, note);
        // 通知顧客（M01），交易 commit 後才寄
        eventPublisher.publishEvent(new OrderEvent(orderNo, OrderEventType.CONFIRMED));
        return toDetail(order);
    }

    /** 出貨（備貨中 → 已出貨），填物流單號 */
    @Transactional
    public AdminOrderDetailResponse ship(String orderNo, long version, String trackingNo, Long adminId, String note) {
        Order order = loadAndCheckVersion(orderNo, version);
        order.setTrackingNo(trackingNo);
        changeStatus(order, OrderStatus.SHIPPED, adminId, note);
        eventPublisher.publishEvent(new OrderEvent(orderNo, OrderEventType.SHIPPED));
        return toDetail(order);
    }

    /** 已送達／已到店（已出貨 → 已送達） */
    @Transactional
    public AdminOrderDetailResponse deliver(String orderNo, long version, Long adminId, String note) {
        Order order = loadAndCheckVersion(orderNo, version);
        changeStatus(order, OrderStatus.DELIVERED, adminId, note);
        // 宅配訂單排定 N 天後自動完成（M02），交易 commit 後才排
        eventPublisher.publishEvent(new OrderDeliveredEvent(orderNo, order.getShippingMethod()));
        return toDetail(order);
    }

    /**
     * 完成（已送達 → 已完成）。貨到付款的錢在顧客簽收／取件時收到，所以同時標記為已付款
     * （線上付款的訂單早就付過了，不會動到原本的付款時間，見 {@link #markPaidIfNeeded}）。
     */
    @Transactional
    public AdminOrderDetailResponse complete(String orderNo, long version, Long adminId, String note) {
        Order order = loadAndCheckVersion(orderNo, version);
        markPaidIfNeeded(order);
        changeStatus(order, OrderStatus.COMPLETED, adminId, note);
        return toDetail(order);
    }

    /**
     * 系統自動完成（M02：宅配送達超過 N 天，由延遲任務呼叫）。
     *
     * <p>和 {@link #complete} 的差別：
     * <ul>
     *   <li><b>不比對版本號</b>：沒有「管理員看到的畫面」，而是直接看目前的狀態。
     *       N 天裡家人可能已經手動完成或改成已退回 → 不是「已送達」就什麼都不做，回傳 false。
     *       同一則訊息處理兩次，第二次也只是 false（冪等）</li>
     *   <li><b>操作者是 NULL</b>（狀態紀錄顯示「系統」，見 V1 的 order_status_histories）</li>
     *   <li>和家人同時按「完成」：兩邊都讀到「已送達」，但 @Version 讓後寫入的那個失敗 rollback，
     *       不會有兩筆「→ 已完成」的紀錄。輸的如果是這裡，例外會讓訊息重試（M03），
     *       重試時訂單已經是「已完成」→ 回傳 false，結束</li>
     *   <li>這是 M02 寫的，當時還沒有悲觀鎖的寫法（P01 才加）。只會和「家人手動完成／退回」競爭，
     *       靠 @Version＋重試就夠，所以沒有改成 FOR UPDATE</li>
     * </ul>
     *
     * @return 這次有把訂單改成已完成
     */
    @Transactional
    public boolean autoComplete(String orderNo, String note) {
        Order order = orderRepository.findByOrderNo(orderNo).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.DELIVERED) {
            return false;
        }
        markPaidIfNeeded(order);
        changeStatus(order, OrderStatus.COMPLETED, null, note);
        return true;
    }

    /**
     * 線上付款成功（P01，由金流通知呼叫）。
     *
     * <p>用 {@link OrderRepository#findForUpdateByOrderNo} 鎖住訂單（PaymentService 已經先鎖了，這裡是同一個交易，
     * 拿到的是同一個物件）：和「逾時未付款自動取消」同時發生時，
     * 後到的一方會等前一方做完，再依最新狀態處理，不會「已付款卻被取消」或「取消後又被改成備貨中」。
     *
     * <ul>
     *   <li>待付款 → 備貨中、已付款，通知顧客（和家人按「確認訂單」寄同一種信）</li>
     *   <li>已取消（逾時、顧客或家人取消）→ 訂單<b>維持已取消</b>（庫存已經還回去了），標記已付款，
     *       狀態紀錄寫「請退款」，後台的訂單詳情頁會顯示紅色的退款提醒（列表上目前看不出來，見 NOTES 的 P01）</li>
     *   <li>已經付過款 → 重複付款，同樣記錄「請退款」</li>
     * </ul>
     * 已經處理過的同一筆付款（金流重複通知）在 PaymentService 就擋掉了，不會進來這裡。
     */
    @Transactional
    public OnlinePaymentOutcome onlinePaymentReceived(String orderNo, String providerTradeNo) {
        Order order = orderRepository.findForUpdateByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("找不到訂單"));
        Instant now = Instant.now();
        if (order.getPaymentStatus() == PaymentStatus.PAID) {
            recordWithoutStatusChange(order, "重複付款（綠界交易編號 " + providerTradeNo + "），請到綠界後台退款其中一筆");
            return OnlinePaymentOutcome.DUPLICATE_PAYMENT;
        }
        order.setPaymentStatus(PaymentStatus.PAID);
        order.setPaidAt(now);
        if (order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            orderRepository.saveAndFlush(order);
            recordWithoutStatusChange(order, "訂單" + order.getStatus().label() + "後才收到線上付款（綠界交易編號 "
                    + providerTradeNo + "），請到綠界後台退款");
            return OnlinePaymentOutcome.PAID_AFTER_CANCEL;
        }
        changeStatus(order, OrderStatus.PROCESSING, null, "線上付款成功（綠界交易編號 " + providerTradeNo + "）");
        eventPublisher.publishEvent(new OrderEvent(orderNo, OrderEventType.CONFIRMED));
        return OnlinePaymentOutcome.CONFIRMED;
    }

    /**
     * 逾時未付款，系統自動取消並歸還庫存（P01＋M02 的延遲任務呼叫）。
     *
     * <p>和 {@link #autoComplete} 一樣冪等：不是「待付款」（已經付款、顧客自己取消了）就什麼都不做。
     * 鎖住訂單的原因見 {@link #onlinePaymentReceived}。
     *
     * @return 這次有把訂單取消
     */
    @Transactional
    public boolean autoCancelUnpaid(String orderNo, String note) {
        Order order = orderRepository.findForUpdateByOrderNo(orderNo).orElse(null);
        if (order == null || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            return false;
        }
        changeStatus(order, OrderStatus.CANCELLED, null, note);
        orderStockService.restore(order);
        return true;
    }

    /**
     * 物流狀態通知（P03，綠界）→ 自動更新訂單。和其他系統操作一樣先鎖住訂單、依最新狀態處理，
     * 重複通知、順序錯亂（例如「到店」的通知沒收到就先收到「已取件」）都不會出錯：
     *
     * <ul>
     *   <li>到店：已出貨 → 已送達</li>
     *   <li>已取件：→ 已完成＋已付款（取貨付款在取件時收錢）；還是「已出貨」就先補一筆「已送達」</li>
     *   <li>未取件／退回寄件門市：已出貨、已送達 → 已退回。<b>不自動加回庫存</b>：包裹還在路上，
     *       收到後確認商品沒壞再到商品管理調整（和管理員手動「退回」時選擇不加回庫存一樣）</li>
     *   <li>其他情況（寄件、重複通知、訂單已經是後面的狀態）：只留一筆紀錄，狀態不變</li>
     * </ul>
     *
     * @return 這次有改變訂單狀態
     */
    @Transactional
    public boolean logisticsStatusChanged(String orderNo, LogisticsEvent event, String note) {
        Order order = orderRepository.findForUpdateByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("找不到訂單"));
        OrderStatus status = order.getStatus();
        switch (event) {
            case ARRIVED -> {
                if (status == OrderStatus.SHIPPED) {
                    changeStatus(order, OrderStatus.DELIVERED, null, note);
                    return true;
                }
            }
            case PICKED_UP -> {
                if (status == OrderStatus.SHIPPED) {
                    changeStatus(order, OrderStatus.DELIVERED, null, "（到店通知沒收到，依取件通知補上）");
                    status = OrderStatus.DELIVERED;
                }
                if (status == OrderStatus.DELIVERED) {
                    markPaidIfNeeded(order);
                    changeStatus(order, OrderStatus.COMPLETED, null, note);
                    return true;
                }
            }
            case NOT_PICKED_UP, RETURNED_TO_SENDER -> {
                if (status == OrderStatus.SHIPPED || status == OrderStatus.DELIVERED) {
                    String refund = order.getPaymentStatus() == PaymentStatus.PAID ? "已線上付款，請到綠界後台退款；" : "";
                    changeStatus(order, OrderStatus.RETURNED, null,
                            note + "（" + refund + "包裹退回後請確認商品，再到商品管理調整庫存）");
                    return true;
                }
            }
            case DROPPED_OFF -> {
                // 只記錄
            }
        }
        recordWithoutStatusChange(order, note);
        return false;
    }

    /** 狀態沒變、但要留下紀錄（例如需要退款），操作者是系統 */
    private void recordWithoutStatusChange(Order order, String note) {
        historyRepository.save(new OrderStatusHistory(order, order.getStatus(), order.getStatus(), null, note));
    }

    /**
     * 管理員取消（出貨前都可以，由狀態機決定），歸還庫存。
     * 和顧客取消不同：顧客只能在「待確認／待付款」時取消。
     *
     * <p>取消「已線上付款」的訂單（備貨中）：訂單變成「已取消＋已付款」，後台詳情頁會提醒到綠界後台退款。
     * 退款目前是人工處理，程式不會把付款狀態改成 REFUNDED（見 {@link PaymentStatus}）。
     */
    @Transactional
    public AdminOrderDetailResponse cancel(String orderNo, long version, Long adminId, String note) {
        Order order = loadAndCheckVersion(orderNo, version);
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
        Order order = loadAndCheckVersion(orderNo, version);
        String fullNote = (restock ? "已歸還庫存" : "未歸還庫存") + (note == null || note.isBlank() ? "" : "；" + note);
        changeStatus(order, OrderStatus.RETURNED, adminId, fullNote);
        if (restock) {
            orderStockService.restore(order);
        }
        return toDetail(order);
    }

    // ===== helpers =====

    /**
     * 讀出訂單並比對版本號：不同代表管理員看到的是舊資料（樂觀鎖的「跨請求」那一半）。
     * 這裡<b>沒有</b>鎖住資料列；真的加鎖（SELECT ... FOR UPDATE）的是 {@link OrderRepository#findForUpdateByOrderNo}，給系統操作用
     */
    private Order loadAndCheckVersion(String orderNo, long expectedVersion) {
        Order order = findOrThrow(orderNo);
        if (order.getVersion() != expectedVersion) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        return order;
    }

    /**
     * 經過狀態機改狀態、立即寫入（@Version 檢查在這裡發生），再寫狀態紀錄。
     * 先 flush 訂單再做其他事（例如歸還庫存）：版本衝突的話在這裡就失敗。
     *
     * @param operatorId 操作的管理員；系統自動處理時傳 null（order_status_histories.operator_id 是 NULL，畫面顯示「系統」）
     */
    private void changeStatus(Order order, OrderStatus target, Long operatorId, String note) {
        OrderStatus from = order.transitionTo(target, Instant.now());
        orderRepository.saveAndFlush(order);
        historyRepository.save(new OrderStatusHistory(
                order, from, target, operatorId == null ? null : userRepository.getReferenceById(operatorId), note));
    }

    /**
     * 標記已付款，並記下付款時間；<b>已經付過的不動</b>。
     * 線上付款的訂單在金流通知時就付款了，之後「完成」不能把付款時間蓋成完成的時間（查帳時要對得上綠界的紀錄）。
     */
    private void markPaidIfNeeded(Order order) {
        if (order.getPaymentStatus() != PaymentStatus.PAID) {
            order.setPaymentStatus(PaymentStatus.PAID);
            order.setPaidAt(Instant.now());
        }
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
