package com.mamashop.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.ecpay.EcpayCheckMacValue;
import com.mamashop.common.ecpay.EcpayCheckMacValueTest;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentMethod;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.service.AdminOrderService;
import com.mamashop.order.service.OrderService;
import com.mamashop.payment.dto.PaymentFormResponse;
import com.mamashop.payment.service.PaymentService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 線上付款（P01）：下單 → 前往付款 → 綠界通知 → 訂單備貨中。
 *
 * <p>綠界的通知用 MockMvc 送到真的 API（form 格式、不帶 JWT），檢查碼用綠界公開測試商店的 HashKey／HashIV 計算，
 * 等於「模擬綠界的伺服器」。資料庫是真的，併發測試才看得出鎖有沒有作用。
 */
@SpringBootTest(properties = {
        "app.payment.ecpay.enabled=true",
        "app.payment.ecpay.merchant-id=3002607",
        "app.payment.ecpay.hash-key=" + EcpayCheckMacValueTest.HASH_KEY,
        "app.payment.ecpay.hash-iv=" + EcpayCheckMacValueTest.HASH_IV,
        "app.payment.ecpay.site-url=https://shop.example.com/"})
@AutoConfigureMockMvc
class PaymentIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrderService orderService;
    @Autowired private AdminOrderService adminOrderService;
    @Autowired private PaymentService paymentService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;

    private Category category;
    private Product product;
    private User customer;
    private User otherCustomer;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("付款測試-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("付款測試包");
        product.setPrice(new BigDecimal("790.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = user("pay-" + suffix);
        otherCustomer = user("pay-other-" + suffix);
    }

    @AfterEach
    void cleanUp() {
        for (User user : List.of(customer, otherCustomer)) {
            jdbc.update("DELETE FROM payments WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", user.getId());
            jdbc.update("DELETE FROM order_status_histories WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", user.getId());
            jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", user.getId());
            jdbc.update("DELETE FROM orders WHERE user_id = ?", user.getId());
        }
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id IN (?, ?)", customer.getId(), otherCustomer.getId());
    }

    // ===== 下單與前往付款 =====

    @Test
    void onlineOrder_startsAsPendingPayment_andBuildsSignedForm() {
        String orderNo = onlineOrder();
        assertThat(order(orderNo).order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);

        PaymentFormResponse form = paymentService.startPayment(customer.getId(), orderNo);

        assertThat(form.action()).isEqualTo("https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5");
        Map<String, String> fields = form.fields();
        assertThat(fields).containsEntry("MerchantTradeNo", orderNo + "01")
                .containsEntry("TotalAmount", "850") // 790 + 運費 60（超商）
                .containsEntry("ChoosePayment", "Credit")
                .containsEntry("ReturnURL", "https://shop.example.com/api/payments/ecpay/notify")
                .containsEntry("ClientBackURL", "https://shop.example.com/orders/" + orderNo + "?from=payment");
        assertThat(fields.get("ItemName")).isEqualTo("付款測試包 x 1#運費 x 1");
        assertThat(EcpayCheckMacValue.verify(fields, EcpayCheckMacValueTest.HASH_KEY, EcpayCheckMacValueTest.HASH_IV)).isTrue();

        // 再按一次「前往付款」：新的交易編號（綠界不能重複使用）
        assertThat(paymentService.startPayment(customer.getId(), orderNo).fields())
                .containsEntry("MerchantTradeNo", orderNo + "02");
    }

    @Test
    void startPayment_rejectsOthersOrders_andNonPendingOrders() {
        String orderNo = onlineOrder();
        // 別人的訂單：和不存在一樣回 404
        assertThatThrownBy(() -> paymentService.startPayment(otherCustomer.getId(), orderNo))
                .isInstanceOf(NotFoundException.class);

        // 貨到付款的訂單不能線上付款
        String codOrder = orderService.createOrder(customer.getId(), request(PaymentMethod.COD)).orderNo();
        assertThatThrownBy(() -> paymentService.startPayment(customer.getId(), codOrder))
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_ALLOWED);

        // 付完款之後不能再付
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");
        notifyPaid(tradeNo, "850");
        assertThatThrownBy(() -> paymentService.startPayment(customer.getId(), orderNo))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(ErrorCode.PAYMENT_NOT_ALLOWED);
    }

    // ===== 綠界通知 =====

    @Test
    void paidNotification_confirmsOrder() throws Exception {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");

        mockMvc.perform(notification(signed(paidParams(tradeNo, "850"))))
                .andExpect(status().isOk())
                .andExpect(content().string("1|OK"));

        AdminOrderDetailResponse detail = order(orderNo);
        assertThat(detail.order().status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(detail.order().paymentStatus()).isEqualTo(PaymentStatus.PAID);
        AdminOrderDetailResponse.HistoryResponse last = detail.histories().getLast();
        assertThat(last.operatorName()).isEqualTo("系統");
        assertThat(last.note()).contains("線上付款成功").contains("2511140000000001");
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE merchant_trade_no = ?", String.class, tradeNo))
                .isEqualTo("PAID");
    }

    @Test
    void duplicateNotification_isProcessedOnce() throws Exception {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");

        for (int i = 0; i < 3; i++) {
            mockMvc.perform(notification(signed(paidParams(tradeNo, "850")))).andExpect(content().string("1|OK"));
        }

        assertThat(order(orderNo).histories()).filteredOn(h -> h.toStatus() == OrderStatus.PROCESSING).hasSize(1);
    }

    @Test
    void forgedOrTamperedNotification_isRejected() throws Exception {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");

        // 沒有檢查碼（有人自己打 API 想把訂單改成已付款）
        mockMvc.perform(notification(paidParams(tradeNo, "850")))
                .andExpect(content().string("0|CheckMacValue Error"));
        // 簽好之後改金額
        Map<String, String> tampered = signed(paidParams(tradeNo, "850"));
        tampered.put("TradeAmt", "1");
        mockMvc.perform(notification(tampered)).andExpect(content().string("0|CheckMacValue Error"));
        // 檢查碼正確但金額和訂單不符（防我們自己的 bug）
        mockMvc.perform(notification(signed(paidParams(tradeNo, "1"))))
                .andExpect(content().string("0|TradeAmt Error"));
        // 綠界測試後台的「模擬付款」：沒有真的收到錢
        Map<String, String> simulated = paidParams(tradeNo, "850");
        simulated.put("SimulatePaid", "1");
        mockMvc.perform(notification(signed(simulated))).andExpect(content().string("1|OK"));

        assertThat(order(orderNo).order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(order(orderNo).order().paymentStatus()).isEqualTo(PaymentStatus.UNPAID);
    }

    @Test
    void failedNotification_keepsOrderPending_andCustomerCanRetry() throws Exception {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");
        Map<String, String> failed = paidParams(tradeNo, "850");
        failed.put("RtnCode", "10100248");
        failed.put("RtnMsg", "拒絕交易");

        mockMvc.perform(notification(signed(failed))).andExpect(content().string("1|OK"));

        assertThat(order(orderNo).order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        assertThat(jdbc.queryForObject("SELECT status FROM payments WHERE merchant_trade_no = ?", String.class, tradeNo))
                .isEqualTo("FAILED");
        assertThat(paymentService.startPayment(customer.getId(), orderNo).fields())
                .containsEntry("MerchantTradeNo", orderNo + "02");
    }

    // ===== 後台操作 =====

    @Test
    void adminCannotConfirmUnpaidOnlineOrder_butCanAfterPayment_andPaidAtIsKept() {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");

        // 還沒付款：管理員不能手動確認（只有金流通知能把待付款改成備貨中）
        assertThatThrownBy(() -> adminOrderService.confirm(orderNo, 0, customer.getId(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.INVALID_ORDER_STATUS);
        assertThat(order(orderNo).order().status()).isEqualTo(OrderStatus.PENDING_PAYMENT);
        // 顧客還是可以付款
        notifyPaid(tradeNo, "850");

        AdminOrderDetailResponse paid = order(orderNo);
        assertThat(paid.order().status()).isEqualTo(OrderStatus.PROCESSING);
        java.time.Instant paidAt = paid.order().paidAt();

        // 之後照一般流程出貨、送達、完成：付款時間維持金流通知的那一刻，不會被「完成」蓋掉
        long v = paid.version();
        v = adminOrderService.ship(orderNo, v, "T1", customer.getId(), null).version();
        v = adminOrderService.deliver(orderNo, v, customer.getId(), null).version();
        AdminOrderDetailResponse completed = adminOrderService.complete(orderNo, v, customer.getId(), null);
        assertThat(completed.order().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(completed.order().paidAt()).isEqualTo(paidAt);
    }

    // ===== 逾時取消 vs 付款 =====

    @Test
    void autoCancelUnpaid_restoresStock_andLatePaymentIsFlaggedForRefund() {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");
        assertThat(stock()).isEqualTo(9);

        assertThat(adminOrderService.autoCancelUnpaid(orderNo, "逾時")).isTrue();
        assertThat(stock()).isEqualTo(10);
        assertThat(adminOrderService.autoCancelUnpaid(orderNo, "逾時")).isFalse(); // 冪等

        // 取消之後顧客才在綠界付完款
        notifyPaid(tradeNo, "850");
        AdminOrderDetailResponse detail = order(orderNo);
        assertThat(detail.order().status()).isEqualTo(OrderStatus.CANCELLED); // 不會「復活」，庫存已經還回去了
        assertThat(detail.order().paymentStatus()).isEqualTo(PaymentStatus.PAID); // 後台看到「已取消＋已付款」→ 要退款
        assertThat(detail.histories().getLast().note()).contains("請到綠界後台退款");
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void paidThenTimeout_isNotCancelled() {
        String orderNo = onlineOrder();
        notifyPaid(paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo"), "850");

        assertThat(adminOrderService.autoCancelUnpaid(orderNo, "逾時")).isFalse();
        assertThat(order(orderNo).order().status()).isEqualTo(OrderStatus.PROCESSING);
        assertThat(stock()).isEqualTo(9);
    }

    /**
     * 付款通知和逾時取消「同一瞬間」發生（各 10 次）：訂單被鎖住，後到的一方等前一方做完再看最新狀態。
     * 不管誰先，結果一定是這兩種之一，不會有「已付款卻被取消、庫存還回去又出貨」：
     * <ul>
     *   <li>付款先：備貨中、已付款、庫存 9</li>
     *   <li>取消先：已取消、已付款（標記退款）、庫存 10</li>
     * </ul>
     */
    @Test
    void paymentAndTimeoutAtTheSameTime_endInConsistentState() throws Exception {
        for (int round = 0; round < 10; round++) {
            String orderNo = onlineOrder();
            String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");
            int stockBefore = stock();

            runConcurrently(List.of(
                    () -> paymentService.handleEcpayNotification(signed(paidParams(tradeNo, "850"))),
                    () -> adminOrderService.autoCancelUnpaid(orderNo, "逾時")));

            AdminOrderDetailResponse detail = order(orderNo);
            assertThat(detail.order().paymentStatus()).isEqualTo(PaymentStatus.PAID);
            if (detail.order().status() == OrderStatus.PROCESSING) {
                assertThat(stock()).isEqualTo(stockBefore);
            } else {
                assertThat(detail.order().status()).isEqualTo(OrderStatus.CANCELLED);
                assertThat(stock()).isEqualTo(stockBefore + 1);
                assertThat(detail.histories().getLast().note()).contains("請到綠界後台退款");
            }
        }
    }

    /** 綠界同一筆通知同時送 5 次：只處理一次（一筆「→ 備貨中」紀錄） */
    @Test
    void duplicateNotificationsAtTheSameTime_areProcessedOnce() throws Exception {
        String orderNo = onlineOrder();
        String tradeNo = paymentService.startPayment(customer.getId(), orderNo).fields().get("MerchantTradeNo");
        List<Callable<Object>> tasks = new ArrayList<>();
        for (int i = 0; i < 5; i++) {
            tasks.add(() -> paymentService.handleEcpayNotification(signed(paidParams(tradeNo, "850"))));
        }

        List<Object> results = runConcurrently(tasks);

        assertThat(results).containsOnly("1|OK");
        assertThat(order(orderNo).histories()).filteredOn(h -> h.toStatus() == OrderStatus.PROCESSING).hasSize(1);
    }

    // ===== helpers =====

    private String onlineOrder() {
        return orderService.createOrder(customer.getId(), request(PaymentMethod.ONLINE)).orderNo();
    }

    private CreateOrderRequest request(PaymentMethod paymentMethod) {
        return new CreateOrderRequest(List.of(new CreateOrderRequest.Item(product.getId(), 1)),
                ShippingMethodCode.CVS_PICKUP, "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市",
                null, paymentMethod);
    }

    private AdminOrderDetailResponse order(String orderNo) {
        return adminOrderService.get(orderNo);
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }

    private void notifyPaid(String tradeNo, String amount) {
        assertThat(paymentService.handleEcpayNotification(signed(paidParams(tradeNo, amount)))).isEqualTo("1|OK");
    }

    /** 綠界付款成功通知的參數（欄位名稱照綠界文件） */
    private static Map<String, String> paidParams(String merchantTradeNo, String amount) {
        Map<String, String> params = new HashMap<>();
        params.put("MerchantID", "3002607");
        params.put("MerchantTradeNo", merchantTradeNo);
        params.put("RtnCode", "1");
        params.put("RtnMsg", "交易成功");
        params.put("TradeNo", "2511140000000001");
        params.put("TradeAmt", amount);
        params.put("PaymentDate", "2026/11/14 12:00:00");
        params.put("PaymentType", "Credit_CreditCard");
        params.put("PaymentTypeChargeFee", "17");
        params.put("TradeDate", "2026/11/14 11:59:00");
        params.put("SimulatePaid", "0");
        params.put("StoreID", "");
        params.put("CustomField1", "");
        return params;
    }

    private static Map<String, String> signed(Map<String, String> params) {
        Map<String, String> copy = new HashMap<>(params);
        copy.put("CheckMacValue", EcpayCheckMacValue.compute(copy, EcpayCheckMacValueTest.HASH_KEY, EcpayCheckMacValueTest.HASH_IV));
        return copy;
    }

    private static MockHttpServletRequestBuilder notification(Map<String, String> params) {
        MockHttpServletRequestBuilder request = post("/api/payments/ecpay/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        params.forEach(request::param);
        return request;
    }

    /** 所有工作準備好後同一瞬間開始（CountDownLatch），等全部做完 */
    private static List<Object> runConcurrently(List<Callable<Object>> tasks) throws Exception {
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService pool = Executors.newFixedThreadPool(tasks.size());
        try {
            List<Future<Object>> futures = new ArrayList<>();
            for (Callable<Object> task : tasks) {
                futures.add(pool.submit(() -> {
                    start.await();
                    return task.call();
                }));
            }
            start.countDown();
            List<Object> results = new ArrayList<>();
            for (Future<Object> future : futures) {
                results.add(future.get());
            }
            return results;
        } finally {
            pool.shutdown();
        }
    }

    private User user(String prefix) {
        User user = new User();
        user.setEmail(prefix + "@example.com");
        user.setPasswordHash("not-used");
        user.setName("付款測試");
        user.setRole(Role.USER);
        return userRepository.save(user);
    }
}
