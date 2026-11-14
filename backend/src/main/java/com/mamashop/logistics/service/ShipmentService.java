package com.mamashop.logistics.service;

import com.mamashop.common.ecpay.EcpayCheckMacValue;
import com.mamashop.common.ecpay.EcpayCheckMacValue.Algorithm;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.logistics.config.EcpayLogisticsProperties;
import com.mamashop.logistics.dto.LabelFormResponse;
import com.mamashop.logistics.dto.ShipmentResponse;
import com.mamashop.logistics.entity.Shipment;
import com.mamashop.logistics.repository.ShipmentRepository;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.order.service.AdminOrderService;
import com.mamashop.order.service.LogisticsEvent;
import com.mamashop.shipping.entity.ShippingMethodCode;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;

/**
 * 綠界物流單（P03）：建立物流單並出貨、列印寄件單、接收物流狀態通知。
 *
 * <pre>
 *  後台按「建立物流單並出貨」→ {@link #createShipment}
 *    鎖住訂單 → 檢查版本號與狀態 → 呼叫綠界建立物流單（MD5 簽章）→ 驗證回應的簽章
 *    → 存物流單 → 訂單「已出貨」，物流單號 = 寄件代碼（寄出貨通知信，M01）
 *  家人列印寄件單（或在 ibon／FamiPort 輸入寄件代碼）→ 到超商寄件
 *  綠界送物流狀態通知 → {@link #handleStatusNotification}
 *    驗證簽章 → 鎖住訂單 → 更新物流單狀態 → 到店／取件／未取件 → 自動更新訂單（AdminOrderService）
 * </pre>
 *
 * <h2>呼叫外部 API 時訂單是鎖住的</h2>
 * 建立物流單時，從檢查狀態到改成「已出貨」都在同一個交易、訂單一直鎖著（最多等綠界 15 秒）。
 * 好處：兩位家人同時按、或顧客同時取消，不會建立兩張物流單、也不會「取消了卻還寄出」。
 * 代價：綠界很慢的時候，這張訂單的其他操作要等。一張訂單、幾秒鐘，家庭小店可以接受。
 * <p>⚠️ 綠界建立成功、但我們之後的寫入失敗（極少見）：綠界那邊有一張沒人用的物流單，
 * 交易回滾、訂單還是「備貨中」，log 會記 ERROR，可以到綠界後台刪除。
 */
@Service
public class ShipmentService {

    private static final Logger log = LoggerFactory.getLogger(ShipmentService.class);

    public static final String ECPAY_OK = "1|OK";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter ECPAY_TIME = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss");
    /** 超商取貨的金額上限（綠界規定 1～20,000 元） */
    private static final BigDecimal MAX_AMOUNT = new BigDecimal("20000");
    /** 不放真的商品名稱：綠界對商品名稱有字元限制，而且寄件單上會印出來 */
    private static final String GOODS_NAME = "mamaShop商品";

    /**
     * 綠界物流狀態代碼 → 對訂單的意義。來源：綠界「物流貨態代碼表」（7-ELEVEN 交貨便、全家／萊爾富店到店，含大宗寄倉的同義代碼）。
     * 其他代碼（例如 300 訂單處理中、2030／3024 物流中心驗收）只更新物流單的狀態，不改訂單
     */
    static final Map<Integer, LogisticsEvent> EVENTS = Map.ofEntries(
            Map.entry(2068, LogisticsEvent.DROPPED_OFF),        // 賣家已到門市寄件（7-11、萊爾富）
            Map.entry(3032, LogisticsEvent.DROPPED_OFF),        // 賣家已到門市寄件（全家、萊爾富）
            Map.entry(2063, LogisticsEvent.ARRIVED),            // 包裹配達取件門市
            Map.entry(2073, LogisticsEvent.ARRIVED),            // 包裹配達取件門市
            Map.entry(2098, LogisticsEvent.ARRIVED),            // 包裹重新配達取件門市（7-11）
            Map.entry(3018, LogisticsEvent.ARRIVED),            // 包裹配達取件門市（全家、萊爾富）
            Map.entry(3029, LogisticsEvent.ARRIVED),            // 包裹已配達指定取件門市（全家、萊爾富）
            Map.entry(2067, LogisticsEvent.PICKED_UP),          // 買家已到店取貨
            Map.entry(3022, LogisticsEvent.PICKED_UP),          // 買家已到店取貨（全家、萊爾富）
            Map.entry(2074, LogisticsEvent.NOT_PICKED_UP),      // 買家未取包裹，將退回物流中心
            Map.entry(3020, LogisticsEvent.NOT_PICKED_UP),      // 買家未取包裹，將退回物流中心（全家、萊爾富）
            Map.entry(2099, LogisticsEvent.RETURNED_TO_SENDER), // 包裹重新配達寄件門市（7-11）
            Map.entry(3019, LogisticsEvent.RETURNED_TO_SENDER)  // 包裹已退至原寄件門市（全家、萊爾富）
    );

    private final OrderRepository orderRepository;
    private final ShipmentRepository shipmentRepository;
    private final AdminOrderService adminOrderService;
    private final CvsMapService cvsMapService;
    private final EcpayLogisticsGateway gateway;
    private final EcpayLogisticsProperties properties;
    private final Clock clock = Clock.systemUTC();

    public ShipmentService(OrderRepository orderRepository, ShipmentRepository shipmentRepository,
                           AdminOrderService adminOrderService, CvsMapService cvsMapService,
                           EcpayLogisticsGateway gateway, EcpayLogisticsProperties properties) {
        this.orderRepository = orderRepository;
        this.shipmentRepository = shipmentRepository;
        this.adminOrderService = adminOrderService;
        this.cvsMapService = cvsMapService;
        this.gateway = gateway;
        this.properties = properties;
    }

    // ===== 建立物流單並出貨 =====

    @Transactional
    public AdminOrderDetailResponse createShipment(String orderNo, long version, Long adminId, String note) {
        if (!properties.shipmentEnabled()) {
            throw new BusinessException(ErrorCode.SHIPMENT_UNAVAILABLE);
        }
        // 先鎖、再讀（P01 的 PaymentService 說明過原因）
        Order order = orderRepository.findForUpdateByOrderNo(orderNo)
                .orElseThrow(() -> new NotFoundException("找不到訂單"));
        if (order.getVersion() != version) {
            throw new BusinessException(ErrorCode.CONFLICT);
        }
        if (order.getShippingMethod() != ShippingMethodCode.CVS_PICKUP || order.getStatus() != OrderStatus.PROCESSING) {
            throw new BusinessException(ErrorCode.SHIPMENT_NOT_ALLOWED);
        }
        BigDecimal amount = order.getTotalAmount();
        if (amount.stripTrailingZeros().scale() > 0 || amount.signum() <= 0 || amount.compareTo(MAX_AMOUNT) > 0) {
            throw new BusinessException(ErrorCode.SHIPMENT_AMOUNT_INVALID);
        }
        String subType = cvsMapService.subType(order.getCvsBrand());
        String merchantTradeNo = orderNo + String.format("%02d", shipmentRepository.countByOrderId(order.getId()) + 1);
        // 貨到付款：超商代收貨款；線上付款：已經付過了，不代收。
        // （線上付款的訂單能到「備貨中」一定已付款：只有金流通知能把待付款改成備貨中，見 AdminOrderService#confirm）
        boolean collect = order.getPaymentMethod() == PaymentMethod.COD && order.getPaymentStatus() != PaymentStatus.PAID;
        String wholeDollars = amount.setScale(0).toPlainString();

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("MerchantID", properties.merchantId());
        fields.put("MerchantTradeNo", merchantTradeNo);
        fields.put("MerchantTradeDate", ECPAY_TIME.format(LocalDateTime.ofInstant(clock.instant(), TAIPEI)));
        fields.put("LogisticsType", "CVS");
        fields.put("LogisticsSubType", subType);
        fields.put("GoodsAmount", wholeDollars);
        fields.put("IsCollection", collect ? "Y" : "N");
        if (collect) {
            fields.put("CollectionAmount", wholeDollars);
        }
        fields.put("GoodsName", GOODS_NAME);
        fields.put("SenderName", properties.senderName());
        fields.put("SenderCellPhone", properties.senderCellPhone());
        fields.put("ReceiverName", order.getRecipientName());
        fields.put("ReceiverCellPhone", order.getRecipientPhone());
        fields.put("ReceiverStoreID", order.getCvsStoreCode());
        fields.put("ServerReplyURL", properties.siteUrl() + "/api/logistics/ecpay/notify");
        fields.put(EcpayCheckMacValue.FIELD, sign(fields));

        Map<String, String> result = callCreate(fields, orderNo);
        Shipment shipment = new Shipment(order, merchantTradeNo, result.get("AllPayLogisticsID"), subType,
                result.get("CVSPaymentNo"), emptyToNull(result.get("CVSValidationNo")));
        shipment.updateStatus(parseInt(result.get("RtnCode"), 0), truncate(result.get("RtnMsg")), clock.instant());
        shipmentRepository.save(shipment);
        log.info("訂單 {} 建立綠界物流單 {}（寄件代碼 {}）", orderNo, shipment.getLogisticsId(), shipment.trackingNo());

        // 改成「已出貨」：沿用後台出貨（同一個交易、同一張已鎖住的訂單），物流單號就是寄件代碼，並寄出貨通知信
        String historyNote = "綠界物流單 " + shipment.getLogisticsId() + (note == null || note.isBlank() ? "" : "；" + note);
        return adminOrderService.ship(orderNo, version, shipment.trackingNo(), adminId, historyNote);
    }

    /** 呼叫綠界建立物流單；失敗就把綠界的原因（例如「收件人姓名請設定為…」）告訴管理員 */
    private Map<String, String> callCreate(Map<String, String> fields, String orderNo) {
        String body;
        try {
            body = gateway.post("/Express/Create", fields);
        } catch (RestClientException e) {
            log.warn("訂單 {} 建立物流單時連不到綠界：{}", orderNo, e.toString());
            throw new BusinessException(ErrorCode.SHIPMENT_CREATE_FAILED, "連不到綠界，請稍後再試");
        }
        // 成功：1|key=value&...；失敗：錯誤代碼|原因，例如 10500036|收件人姓名請設定為 4~10 字元
        int bar = body.indexOf('|');
        if (!body.startsWith("1|")) {
            String reason = bar >= 0 ? body.substring(bar + 1) : body;
            log.warn("訂單 {} 綠界建立物流單失敗：{}", orderNo, body);
            throw new BusinessException(ErrorCode.SHIPMENT_CREATE_FAILED, "綠界建立物流單失敗：" + truncate(reason));
        }
        Map<String, String> result = parseResponse(body.substring(2));
        // 回應也有簽章：確認真的是綠界回的（防止連線被攔截竄改寄件代碼）
        if (!EcpayCheckMacValue.verify(result, properties.hashKey(), properties.hashIv(), Algorithm.MD5)) {
            log.error("訂單 {} 綠界建立物流單的回應簽章錯誤，綠界那邊可能已經建立了物流單，請到綠界後台確認：{}", orderNo, body);
            throw new BusinessException(ErrorCode.SHIPMENT_CREATE_FAILED, "綠界的回應無法驗證，請到綠界後台確認");
        }
        if (!properties.merchantId().equals(result.get("MerchantID")) || isBlank(result.get("AllPayLogisticsID"))) {
            throw new BusinessException(ErrorCode.SHIPMENT_CREATE_FAILED, "綠界的回應內容不完整，請到綠界後台確認");
        }
        return result;
    }

    // ===== 查詢、列印 =====

    @Transactional(readOnly = true)
    public Optional<ShipmentResponse> findShipment(String orderNo) {
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow(() -> new NotFoundException("找不到訂單"));
        return shipmentRepository.findFirstByOrderIdOrderByIdDesc(order.getId()).map(ShipmentResponse::from);
    }

    /**
     * 列印寄件單的表單（店到店）。後台開新分頁 POST 到綠界，綠界回傳可以列印的寄件單。
     * 7-ELEVEN 也可以不印，直接在 ibon 輸入寄件代碼
     */
    @Transactional(readOnly = true)
    public LabelFormResponse labelForm(String orderNo) {
        if (!properties.shipmentEnabled()) {
            throw new BusinessException(ErrorCode.SHIPMENT_UNAVAILABLE);
        }
        Order order = orderRepository.findByOrderNo(orderNo).orElseThrow(() -> new NotFoundException("找不到訂單"));
        Shipment shipment = shipmentRepository.findFirstByOrderIdOrderByIdDesc(order.getId())
                .orElseThrow(() -> new BusinessException(ErrorCode.SHIPMENT_NOT_FOUND));
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("MerchantID", properties.merchantId());
        fields.put("AllPayLogisticsID", shipment.getLogisticsId());
        String path = switch (shipment.getLogisticsSubType()) {
            case "UNIMARTC2C" -> {
                fields.put("CVSPaymentNo", shipment.getCvsPaymentNo());
                fields.put("CVSValidationNo", shipment.getCvsValidationNo());
                yield "/Express/PrintUniMartC2COrderInfo";
            }
            case "FAMIC2C" -> {
                fields.put("CVSPaymentNo", shipment.getCvsPaymentNo());
                yield "/Express/PrintFAMIC2COrderInfo";
            }
            case "HILIFEC2C" -> {
                fields.put("CVSPaymentNo", shipment.getCvsPaymentNo());
                yield "/Express/PrintHILIFEC2COrderInfo";
            }
            // 大宗寄倉（B2C）的託運單
            default -> "/helper/printTradeDocument";
        };
        fields.put(EcpayCheckMacValue.FIELD, sign(fields));
        return new LabelFormResponse(properties.apiUrl() + path, fields);
    }

    // ===== 物流狀態通知 =====

    /**
     * 綠界的物流狀態通知（ServerReplyURL）。回傳值直接當作回應內容：1|OK 表示收到，其他值綠界會重送（一天 3 次、共 3 天）。
     */
    @Transactional
    public String handleStatusNotification(Map<String, String> params) {
        if (!properties.shipmentEnabled()) {
            return "0|Disabled";
        }
        if (!EcpayCheckMacValue.verify(params, properties.hashKey(), properties.hashIv(), Algorithm.MD5)) {
            log.warn("綠界物流通知的簽章錯誤，忽略（AllPayLogisticsID={}）", params.get("AllPayLogisticsID"));
            return "0|CheckMacValue Error";
        }
        if (!properties.merchantId().equals(params.get("MerchantID"))) {
            log.warn("綠界物流通知的商店代號不符：{}", params.get("MerchantID"));
            return "0|MerchantID Error";
        }
        String logisticsId = params.get("AllPayLogisticsID");
        String orderNo = shipmentRepository.findOrderNoByLogisticsId(logisticsId).orElse(null);
        if (orderNo == null) {
            log.warn("綠界通知了不存在的物流單 {}", logisticsId);
            return "0|Unknown AllPayLogisticsID";
        }
        // 先鎖訂單、再讀物流單：和後台操作、其他通知排隊
        orderRepository.findForUpdateByOrderNo(orderNo).orElseThrow();
        Shipment shipment = shipmentRepository.findByLogisticsId(logisticsId).orElseThrow();

        int code = parseInt(params.get("RtnCode"), -1);
        String message = truncate(params.get("RtnMsg"));
        Instant at = parseTime(params.get("UpdateStatusDate"));
        if (code == shipment.getStatusCode()) {
            log.info("訂單 {} 物流狀態重複通知（{}），已處理過", orderNo, code);
            return ECPAY_OK;
        }
        // 通知可能晚到、順序錯亂：比目前記錄的還舊就只記 log，不倒退
        if (at.isBefore(shipment.getStatusUpdatedAt())) {
            log.info("訂單 {} 收到較舊的物流狀態 {}（{}），略過", orderNo, code, message);
            return ECPAY_OK;
        }
        shipment.updateStatus(code, message, at);
        LogisticsEvent event = EVENTS.get(code);
        if (event != null) {
            boolean changed = adminOrderService.logisticsStatusChanged(orderNo, event, "物流：" + message + "（" + code + "）");
            log.info("訂單 {} 物流狀態 {} {} → {}", orderNo, code, message, changed ? "已更新訂單" : "只記錄");
        } else {
            log.info("訂單 {} 物流狀態 {} {}", orderNo, code, message);
        }
        return ECPAY_OK;
    }

    // ===== helpers =====

    private String sign(Map<String, String> fields) {
        return EcpayCheckMacValue.compute(fields, properties.hashKey(), properties.hashIv(), Algorithm.MD5);
    }

    /** 綠界的回應：key=value&key=value（值沒有 URL 編碼，例如時間裡有空白、中文直接放） */
    static Map<String, String> parseResponse(String body) {
        Map<String, String> result = new HashMap<>();
        for (String pair : body.split("&")) {
            int eq = pair.indexOf('=');
            if (eq > 0) {
                result.put(pair.substring(0, eq), pair.substring(eq + 1));
            }
        }
        return result;
    }

    private Instant parseTime(String value) {
        try {
            return LocalDateTime.parse(value, ECPAY_TIME).atZone(TAIPEI).toInstant();
        } catch (DateTimeParseException | NullPointerException e) {
            return clock.instant();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 200 ? value : value.substring(0, 200);
    }

    private static String emptyToNull(String value) {
        return value == null || value.isEmpty() ? null : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
