package com.mamashop.payment.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.order.entity.Order;
import com.mamashop.order.entity.OrderItem;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.repository.OrderRepository;
import com.mamashop.order.service.AdminOrderService;
import com.mamashop.order.service.OnlinePaymentOutcome;
import com.mamashop.payment.config.EcpayProperties;
import com.mamashop.payment.dto.PaymentFormResponse;
import com.mamashop.payment.dto.PaymentOptionsResponse;
import com.mamashop.payment.entity.Payment;
import com.mamashop.payment.entity.PaymentAttemptStatus;
import com.mamashop.payment.repository.PaymentRepository;
import com.mamashop.timeout.config.OrderTimeoutProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 線上付款（P01，綠界 ECPay 全方位金流）。
 *
 * <pre>
 *  1. 顧客下單（付款方式 ONLINE）→ 訂單「待付款」，排定 30 分鐘後未付款自動取消
 *  2. 顧客按「前往付款」→ {@link #startPayment}：建立付款紀錄、算好檢查碼 → 前端 POST 表單，整頁跳到綠界
 *  3. 顧客在綠界輸入信用卡（卡號只經過綠界，我們的伺服器碰不到）
 *  4. 綠界「伺服器對伺服器」通知付款結果 → {@link #handleEcpayNotification}：驗證檢查碼 → 訂單改成備貨中
 *  5. 顧客的瀏覽器回到網站（ClientBackURL）→ 只是「回來看訂單」，不代表付款成功
 * </pre>
 *
 * <h2>為什麼只相信第 4 步</h2>
 * 第 5 步是顧客的瀏覽器送的，網址或參數都可以自己打，所以完全不拿來判斷付款。
 * 第 4 步的參數有檢查碼（只有綠界和我們知道 HashKey／HashIV），算出來不一樣就拒絕。
 *
 * <h2>重複通知（冪等）與併發</h2>
 * 綠界沒收到「1|OK」會再送；網路問題也可能同一筆送兩次；也可能剛好和「逾時自動取消」同時發生。
 * 處理通知時<b>第一件事就是鎖住訂單</b>（SELECT ... FOR UPDATE），同一張訂單的通知、取消、前往付款全部排隊，
 * 後到的看到最新狀態：付款紀錄已經是「已付款」就直接回 1|OK，不會再改訂單、再寄一次信。
 *
 * <h2>⚠️ 為什麼一定要「先鎖、再讀」</h2>
 * Hibernate 同一個交易裡讀過的 entity 會放在記憶體（persistence context，一級快取）。
 * 如果先讀了訂單（例如查付款時 JOIN FETCH 訂單）、再用 FOR UPDATE 查一次：
 * 資料庫那邊確實會等鎖、也讀到最新的資料，但 Hibernate 發現這筆訂單已經在記憶體裡了，<b>回傳的是舊的那個物件</b>，
 * 之後依舊的版本號更新 → 失敗。PaymentIntegrationTest 的併發測試一開始就是這樣抓到的。
 * 所以這裡先只查「訂單編號」（不載入 entity），第一次載入訂單就是加鎖的那一次。
 */
@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    /** 綠界回應我們必須回的內容，沒收到會重送通知 */
    public static final String ECPAY_OK = "1|OK";
    private static final ZoneId TAIPEI = ZoneId.of("Asia/Taipei");
    private static final DateTimeFormatter TRADE_DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd HH:mm:ss").withZone(TAIPEI);
    /** 一張訂單最多建立幾次付款（MerchantTradeNo 是訂單編號＋兩位數序號，也不能超過 99） */
    static final int MAX_ATTEMPTS = 20;
    /** 付款期限前多久就不讓顧客再開付款頁：避免付到一半訂單被逾時取消 */
    private static final Duration START_MARGIN = Duration.ofMinutes(10);
    private static final int MAX_ITEM_NAME_LENGTH = 200;

    private final OrderRepository orderRepository;
    private final PaymentRepository paymentRepository;
    private final AdminOrderService adminOrderService;
    private final EcpayProperties ecpay;
    private final OrderTimeoutProperties timeoutProperties;
    private final Clock clock;

    @Autowired
    public PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                          AdminOrderService adminOrderService, EcpayProperties ecpay,
                          OrderTimeoutProperties timeoutProperties) {
        this(orderRepository, paymentRepository, adminOrderService, ecpay, timeoutProperties, Clock.systemUTC());
    }

    /** 測試用：固定時間，驗證付款期限 */
    PaymentService(OrderRepository orderRepository, PaymentRepository paymentRepository,
                   AdminOrderService adminOrderService, EcpayProperties ecpay,
                   OrderTimeoutProperties timeoutProperties, Clock clock) {
        this.orderRepository = orderRepository;
        this.paymentRepository = paymentRepository;
        this.adminOrderService = adminOrderService;
        this.ecpay = ecpay;
        this.timeoutProperties = timeoutProperties;
        this.clock = clock;
    }

    public PaymentOptionsResponse options() {
        return new PaymentOptionsResponse(ecpay.enabled(), timeoutProperties.paymentTimeout().toMinutes());
    }

    /**
     * 建立一次付款，回傳前往綠界付款頁的表單。只能付自己的、待付款的訂單。
     */
    @Transactional
    public PaymentFormResponse startPayment(Long userId, String orderNo) {
        if (!ecpay.enabled()) {
            throw new BusinessException(ErrorCode.PAYMENT_METHOD_UNAVAILABLE);
        }
        // 先鎖住訂單再讀（原因見類別說明）：連按兩次「前往付款」、或剛好被逾時取消時排隊，看到最新狀態。
        // 別人的訂單 404（和查詢訂單一樣，不透露是否存在）。getUser().getId() 不會多查一次 users
        Order order = orderRepository.findForUpdateByOrderNo(orderNo)
                .filter(o -> o.getUser().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("找不到訂單"));
        if (order.getPaymentMethod() != PaymentMethod.ONLINE || order.getStatus() != OrderStatus.PENDING_PAYMENT) {
            throw new BusinessException(ErrorCode.PAYMENT_NOT_ALLOWED);
        }
        Instant now = clock.instant();
        if (timeoutProperties.enabled() && now.isAfter(paymentStartDeadline(order))) {
            throw new BusinessException(ErrorCode.PAYMENT_EXPIRED);
        }
        long attempts = paymentRepository.countByOrderId(order.getId());
        if (attempts >= MAX_ATTEMPTS) {
            throw new BusinessException(ErrorCode.PAYMENT_TOO_MANY_ATTEMPTS);
        }

        String merchantTradeNo = order.getOrderNo() + String.format("%02d", attempts + 1);
        paymentRepository.save(new Payment(order, merchantTradeNo, order.getTotalAmount()));

        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("MerchantID", ecpay.merchantId());
        fields.put("MerchantTradeNo", merchantTradeNo);
        fields.put("MerchantTradeDate", TRADE_DATE.format(now));
        fields.put("PaymentType", "aio");
        fields.put("TotalAmount", toWholeDollars(order.getTotalAmount()));
        fields.put("TradeDesc", "mamaShop 訂單 " + order.getOrderNo());
        fields.put("ItemName", itemName(order));
        // 綠界伺服器通知付款結果的網址（要是外面連得到的網址）
        fields.put("ReturnURL", ecpay.siteUrl() + "/api/payments/ecpay/notify");
        // 付款頁上「返回商店」的連結：回到訂單頁
        fields.put("ClientBackURL", ecpay.siteUrl() + "/orders/" + order.getOrderNo() + "?from=payment");
        // 只開信用卡：即時知道結果。ATM、超商代碼要等顧客去繳費，可能超過付款期限
        fields.put("ChoosePayment", "Credit");
        fields.put("EncryptType", "1");
        fields.put(EcpayCheckMacValue.FIELD, EcpayCheckMacValue.compute(fields, ecpay.hashKey(), ecpay.hashIv()));
        log.info("訂單 {} 建立付款 {}", order.getOrderNo(), merchantTradeNo);
        return new PaymentFormResponse(ecpay.cashierUrl(), fields);
    }

    /**
     * 綠界的付款結果通知（ReturnURL）。回傳值直接當作 HTTP 回應內容：1|OK 表示收到，其他值綠界會重送。
     */
    @Transactional
    public String handleEcpayNotification(Map<String, String> params) {
        if (!ecpay.enabled()) {
            return "0|Disabled";
        }
        if (!EcpayCheckMacValue.verify(params, ecpay.hashKey(), ecpay.hashIv())) {
            // 可能是有人偽造通知；不透露原因細節
            log.warn("綠界通知的檢查碼錯誤，忽略（MerchantTradeNo={}）", params.get("MerchantTradeNo"));
            return "0|CheckMacValue Error";
        }
        if (!ecpay.merchantId().equals(params.get("MerchantID"))) {
            log.warn("綠界通知的商店代號不符：{}", params.get("MerchantID"));
            return "0|MerchantID Error";
        }
        String merchantTradeNo = params.get("MerchantTradeNo");
        String orderNo = paymentRepository.findOrderNoByMerchantTradeNo(merchantTradeNo).orElse(null);
        if (orderNo == null) {
            log.warn("綠界通知了不存在的交易 {}", merchantTradeNo);
            return "0|Unknown MerchantTradeNo";
        }
        // 先鎖訂單（第一次載入就加鎖），之後才讀付款紀錄
        orderRepository.findForUpdateByOrderNo(orderNo).orElseThrow();
        Payment payment = paymentRepository.findByMerchantTradeNo(merchantTradeNo).orElseThrow();
        // 重複通知：這筆已經處理過了
        if (payment.getStatus() == PaymentAttemptStatus.PAID) {
            log.info("綠界重複通知 {}，已處理過", merchantTradeNo);
            return ECPAY_OK;
        }
        // 綠界測試後台的「模擬付款」：沒有真的收到錢，不能出貨
        if ("1".equals(params.get("SimulatePaid"))) {
            log.warn("收到綠界「模擬付款」通知 {}，不更改訂單", merchantTradeNo);
            return ECPAY_OK;
        }
        // 金額要和建立交易時一樣（檢查碼已經保證沒被竄改，這裡防我們自己的 bug）
        if (!toWholeDollars(payment.getAmount()).equals(params.get("TradeAmt"))) {
            log.error("綠界通知金額不符：{} 應為 {}，實際 {}", merchantTradeNo, payment.getAmount(), params.get("TradeAmt"));
            return "0|TradeAmt Error";
        }

        Integer resultCode = parseIntOrNull(params.get("RtnCode"));
        String providerTradeNo = params.get("TradeNo");
        if (resultCode == null || resultCode != 1) {
            payment.recordResult(PaymentAttemptStatus.FAILED, providerTradeNo, params.get("PaymentType"),
                    resultCode, truncate(params.get("RtnMsg")), null);
            log.info("訂單 {} 付款失敗（{}）：{}", orderNo, resultCode, params.get("RtnMsg"));
            return ECPAY_OK;
        }

        payment.recordResult(PaymentAttemptStatus.PAID, providerTradeNo, params.get("PaymentType"),
                resultCode, truncate(params.get("RtnMsg")), clock.instant());
        OnlinePaymentOutcome outcome = adminOrderService.onlinePaymentReceived(orderNo, providerTradeNo);
        if (outcome == OnlinePaymentOutcome.CONFIRMED) {
            log.info("訂單 {} 線上付款成功（綠界 {}）", orderNo, providerTradeNo);
        } else {
            // 收到錢了但訂單不能出貨：要人工退款，用 ERROR 讓家人注意到
            log.error("訂單 {} 收到線上付款（綠界 {}）但需要退款：{}", orderNo, providerTradeNo, outcome);
        }
        return ECPAY_OK;
    }

    /** 付款期限（下單時間＋N 分鐘）前 START_MARGIN 就不再開新的付款頁；期限太短（測試）時用三分之一 */
    private Instant paymentStartDeadline(Order order) {
        Duration timeout = timeoutProperties.paymentTimeout();
        Duration margin = START_MARGIN.compareTo(timeout.dividedBy(3)) < 0 ? START_MARGIN : timeout.dividedBy(3);
        return order.getCreatedAt().plus(timeout).minus(margin);
    }

    /** 綠界只收整數金額（下單時已檢查過沒有小數） */
    private static String toWholeDollars(BigDecimal amount) {
        return amount.setScale(0, RoundingMode.UNNECESSARY).toPlainString();
    }

    /** 付款頁顯示的商品名稱：多個商品用 # 分隔（綠界的格式），太長就截斷 */
    private static String itemName(Order order) {
        String names = order.getItems().stream()
                .map((OrderItem item) -> item.getProductName() + " x " + item.getQuantity())
                .collect(Collectors.joining("#"));
        if (order.getShippingFee().signum() > 0) {
            names += "#運費 x 1";
        }
        return names.length() > MAX_ITEM_NAME_LENGTH ? names.substring(0, MAX_ITEM_NAME_LENGTH - 3) + "..." : names;
    }

    private static Integer parseIntOrNull(String value) {
        try {
            return value == null ? null : Integer.valueOf(value);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static String truncate(String value) {
        return value == null || value.length() <= 200 ? value : value.substring(0, 200);
    }
}
