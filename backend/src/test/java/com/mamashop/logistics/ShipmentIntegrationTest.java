package com.mamashop.logistics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;

import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.ecpay.EcpayCheckMacValue;
import com.mamashop.common.ecpay.EcpayCheckMacValue.Algorithm;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.logistics.dto.LabelFormResponse;
import com.mamashop.logistics.dto.ShipmentResponse;
import com.mamashop.logistics.service.EcpayLogisticsGateway;
import com.mamashop.logistics.service.ShipmentService;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.entity.PaymentStatus;
import com.mamashop.order.service.AdminOrderService;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.client.ResourceAccessException;

/**
 * 綠界物流單（P03）：建立物流單並出貨、列印寄件單、物流狀態通知自動更新訂單。
 *
 * <p>呼叫綠界的 HTTP 那一層換成假的（EcpayLogisticsGateway），回傳和綠界測試環境一模一樣格式、簽章正確的內容；
 * 簽章、解析、訂單狀態都是真的。狀態通知用 MockMvc 送 form 到真的 API（不帶 JWT），等於「模擬綠界的伺服器」。
 * 真的連到綠界測試環境的測試在 EcpayLogisticsSandboxTest（要另外打開）。
 */
@SpringBootTest(properties = {
        "app.logistics.ecpay.enabled=true",
        "app.logistics.ecpay.merchant-id=" + ShipmentIntegrationTest.MERCHANT_ID,
        "app.logistics.ecpay.hash-key=" + ShipmentIntegrationTest.HASH_KEY,
        "app.logistics.ecpay.hash-iv=" + ShipmentIntegrationTest.HASH_IV,
        "app.logistics.ecpay.sender-name=王媽媽",
        "app.logistics.ecpay.sender-cell-phone=0911222333",
        "app.logistics.ecpay.site-url=https://shop.example.com"})
@AutoConfigureMockMvc
class ShipmentIntegrationTest {

    static final String MERCHANT_ID = "2000933";
    static final String HASH_KEY = "XBERn1YOvpM9nfZc";
    static final String HASH_IV = "h1ONHk4P4yqbl5LK";

    @Autowired private MockMvc mockMvc;
    @Autowired private ShipmentService shipmentService;
    @Autowired private OrderService orderService;
    @Autowired private AdminOrderService adminOrderService;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private EcpayLogisticsGateway gateway;

    private Category category;
    private Product product;
    private User customer;
    private int logisticsSeq = 3800000 + (int) (Math.random() * 100000);

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        category = new Category();
        category.setName("物流測試-" + suffix);
        category = categoryRepository.save(category);
        product = new Product();
        product.setCategory(category);
        product.setName("物流測試包");
        product.setPrice(new BigDecimal("220.00"));
        product.setStock(10);
        product.setStatus(ProductStatus.ON_SALE);
        product = productRepository.save(product);
        customer = new User();
        customer.setEmail("shipment-" + suffix + "@example.com");
        customer.setPasswordHash("not-used");
        customer.setName("物流測試");
        customer.setRole(Role.USER);
        customer = userRepository.save(customer);
    }

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM shipments WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM order_status_histories WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM order_items WHERE order_id IN (SELECT id FROM orders WHERE user_id = ?)", customer.getId());
        jdbc.update("DELETE FROM orders WHERE user_id = ?", customer.getId());
        jdbc.update("DELETE FROM products WHERE category_id = ?", category.getId());
        jdbc.update("DELETE FROM categories WHERE id = ?", category.getId());
        jdbc.update("DELETE FROM users WHERE id = ?", customer.getId());
    }

    // ===== 建立物流單並出貨 =====

    @SuppressWarnings("unchecked")
    @Test
    void createShipment_callsEcpay_thenShipsOrderWithTrackingNo() {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        String logisticsId = stubCreateSuccess("D8858181", "5655");

        AdminOrderDetailResponse result = shipmentService.createShipment(orderNo, 1, customer.getId(), null);

        assertThat(result.order().status()).isEqualTo(OrderStatus.SHIPPED);
        assertThat(result.order().trackingNo()).isEqualTo("D88581815655"); // 7-11：寄件代碼＋驗證碼
        assertThat(result.histories().getLast().note()).contains("綠界物流單 " + logisticsId);

        // 送給綠界的內容
        ArgumentCaptor<Map<String, String>> sent = ArgumentCaptor.forClass(Map.class);
        verify(gateway).post(eq("/Express/Create"), sent.capture());
        Map<String, String> fields = sent.getValue();
        assertThat(fields)
                .containsEntry("MerchantTradeNo", orderNo + "01")
                .containsEntry("LogisticsSubType", "UNIMARTC2C")
                .containsEntry("GoodsAmount", "280") // 220 + 超商運費 60
                .containsEntry("IsCollection", "Y") // 貨到付款：超商代收
                .containsEntry("CollectionAmount", "280")
                .containsEntry("ReceiverName", "王小明")
                .containsEntry("ReceiverStoreID", "131386")
                .containsEntry("SenderName", "王媽媽")
                .containsEntry("ServerReplyURL", "https://shop.example.com/api/logistics/ecpay/notify");
        assertThat(EcpayCheckMacValue.verify(fields, HASH_KEY, HASH_IV, Algorithm.MD5)).isTrue();

        ShipmentResponse shipment = shipmentService.findShipment(orderNo).orElseThrow();
        assertThat(shipment.logisticsId()).isEqualTo(logisticsId);
        assertThat(shipment.statusCode()).isEqualTo(300);
    }

    @Test
    void createShipment_ecpayRejects_showsReasonAndKeepsOrderProcessing() {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        when(gateway.post(eq("/Express/Create"), anyMap()))
                .thenReturn("10500036|收件人姓名請設定為 4~10 字元(中文 2~5 個字, 英文 4~10 個字).");

        assertThatThrownBy(() -> shipmentService.createShipment(orderNo, 1, customer.getId(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("收件人姓名請設定為");
        assertThat(status(orderNo)).isEqualTo(OrderStatus.PROCESSING);
        assertThat(shipmentService.findShipment(orderNo)).isEmpty();
    }

    @Test
    void createShipment_networkErrorOrForgedResponse_isRejected() {
        String orderNo = processingOrder(CvsBrand.FAMILY);
        when(gateway.post(eq("/Express/Create"), anyMap())).thenThrow(new ResourceAccessException("timeout"));
        assertThatThrownBy(() -> shipmentService.createShipment(orderNo, 1, customer.getId(), null))
                .hasMessageContaining("連不到綠界");

        // 回應的簽章不對（被竄改）
        Map<String, String> response = successResponse("16000587843", "");
        response.put("CVSPaymentNo", "99999999999");
        when(gateway.post(eq("/Express/Create"), anyMap())).thenReturn("1|" + join(response));
        assertThatThrownBy(() -> shipmentService.createShipment(orderNo, 1, customer.getId(), null))
                .hasMessageContaining("無法驗證");
        assertThat(status(orderNo)).isEqualTo(OrderStatus.PROCESSING);
    }

    @Test
    void createShipment_checksVersionAndStatus_beforeCallingEcpay() {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        assertThatThrownBy(() -> shipmentService.createShipment(orderNo, 0, customer.getId(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.CONFLICT);

        String homeOrder = orderService.createOrder(customer.getId(), new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(product.getId(), 1)), ShippingMethodCode.HOME_DELIVERY,
                "王小明", "0912345678", "台北市", null, null, null, null)).orderNo();
        adminOrderService.confirm(homeOrder, 0, customer.getId(), null);
        assertThatThrownBy(() -> shipmentService.createShipment(homeOrder, 1, customer.getId(), null))
                .extracting("errorCode").isEqualTo(ErrorCode.SHIPMENT_NOT_ALLOWED);

        verify(gateway, never()).post(any(), anyMap());
    }

    @Test
    void labelForm_sevenEleven() {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        String logisticsId = stubCreateSuccess("D8858181", "5655");
        shipmentService.createShipment(orderNo, 1, customer.getId(), null);

        LabelFormResponse form = shipmentService.labelForm(orderNo);

        assertThat(form.action()).isEqualTo("https://logistics-stage.ecpay.com.tw/Express/PrintUniMartC2COrderInfo");
        assertThat(form.fields()).containsEntry("AllPayLogisticsID", logisticsId)
                .containsEntry("CVSPaymentNo", "D8858181").containsEntry("CVSValidationNo", "5655");
        assertThat(EcpayCheckMacValue.verify(form.fields(), HASH_KEY, HASH_IV, Algorithm.MD5)).isTrue();
    }

    // ===== 物流狀態通知 =====

    @Test
    void notifications_arrivedThenPickedUp_completeOrderAndMarkPaid() throws Exception {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        String logisticsId = stubCreateSuccess("D8858181", "5655");
        shipmentService.createShipment(orderNo, 1, customer.getId(), null);

        notify(logisticsId, 2068, "賣家已到門市寄件", "2026/11/15 10:00:00");
        assertThat(status(orderNo)).isEqualTo(OrderStatus.SHIPPED);

        notify(logisticsId, 2073, "包裹配達取件門市", "2026/11/16 10:00:00");
        assertThat(status(orderNo)).isEqualTo(OrderStatus.DELIVERED);

        notify(logisticsId, 2067, "買家已到店取貨", "2026/11/17 18:30:00");
        AdminOrderDetailResponse detail = adminOrderService.get(orderNo);
        assertThat(detail.order().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(detail.order().paymentStatus()).isEqualTo(PaymentStatus.PAID); // 取貨付款：取件時收到錢
        assertThat(detail.histories().getLast().operatorName()).isEqualTo("系統");
        assertThat(detail.histories().getLast().note()).isEqualTo("物流：買家已到店取貨（2067）");

        // 重複通知：不會再多一筆紀錄
        int histories = detail.histories().size();
        notify(logisticsId, 2067, "買家已到店取貨", "2026/11/17 18:30:00");
        assertThat(adminOrderService.get(orderNo).histories()).hasSize(histories);
        assertThat(shipmentService.findShipment(orderNo).orElseThrow().statusCode()).isEqualTo(2067);
    }

    @Test
    void notification_pickedUpWithoutArrival_fillsInDelivered() throws Exception {
        String orderNo = processingOrder(CvsBrand.FAMILY);
        String logisticsId = stubCreateSuccess("16000587843", "");
        shipmentService.createShipment(orderNo, 1, customer.getId(), null);

        notify(logisticsId, 3022, "買家已到店取貨", "2026/11/17 18:30:00");

        AdminOrderDetailResponse detail = adminOrderService.get(orderNo);
        assertThat(detail.order().status()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(detail.histories()).extracting(AdminOrderDetailResponse.HistoryResponse::toStatus)
                .containsSubsequence(OrderStatus.SHIPPED, OrderStatus.DELIVERED, OrderStatus.COMPLETED);
    }

    @Test
    void notification_notPickedUp_returnsOrder_withoutRestock() throws Exception {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        String logisticsId = stubCreateSuccess("D8858181", "5655");
        shipmentService.createShipment(orderNo, 1, customer.getId(), null);
        notify(logisticsId, 2073, "包裹配達取件門市", "2026/11/16 10:00:00");

        notify(logisticsId, 2074, "買家未取包裹，將退回物流中心", "2026/11/23 23:00:00");

        AdminOrderDetailResponse detail = adminOrderService.get(orderNo);
        assertThat(detail.order().status()).isEqualTo(OrderStatus.RETURNED);
        assertThat(detail.histories().getLast().note()).contains("包裹退回後請確認商品");
        assertThat(stock()).isEqualTo(9); // 包裹還在路上，不自動加回庫存

        // 之後「退回寄件門市」：訂單已經是已退回，只記錄
        notify(logisticsId, 2099, "包裹重新配達寄件門市", "2026/11/26 10:00:00");
        assertThat(adminOrderService.get(orderNo).histories().getLast().note()).contains("包裹重新配達寄件門市");
        assertThat(status(orderNo)).isEqualTo(OrderStatus.RETURNED);
    }

    @Test
    void notification_olderThanCurrent_isIgnored() throws Exception {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        String logisticsId = stubCreateSuccess("D8858181", "5655");
        shipmentService.createShipment(orderNo, 1, customer.getId(), null);
        notify(logisticsId, 2073, "包裹配達取件門市", "2026/11/16 10:00:00");

        // 晚到的舊通知（寄件）
        notify(logisticsId, 2068, "賣家已到門市寄件", "2026/11/15 10:00:00");

        assertThat(shipmentService.findShipment(orderNo).orElseThrow().statusCode()).isEqualTo(2073);
    }

    @Test
    void forgedNotification_isRejected() throws Exception {
        String orderNo = processingOrder(CvsBrand.SEVEN);
        String logisticsId = stubCreateSuccess("D8858181", "5655");
        shipmentService.createShipment(orderNo, 1, customer.getId(), null);
        Map<String, String> params = notificationParams(logisticsId, 2067, "買家已到店取貨", "2026/11/17 18:30:00");

        // 沒有簽章；用 SHA-256 簽（金流的算法）
        mockMvc.perform(notification(params)).andExpect(content().string("0|CheckMacValue Error"));
        params.put("CheckMacValue", EcpayCheckMacValue.compute(params, HASH_KEY, HASH_IV));
        mockMvc.perform(notification(params)).andExpect(content().string("0|CheckMacValue Error"));

        assertThat(status(orderNo)).isEqualTo(OrderStatus.SHIPPED);
    }

    // ===== helpers =====

    /** 超商取貨、貨到付款、家人已確認（版本 1） */
    private String processingOrder(CvsBrand brand) {
        String orderNo = orderService.createOrder(customer.getId(), new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(product.getId(), 1)), ShippingMethodCode.CVS_PICKUP,
                "王小明", "0912345678", null, brand, brand == CvsBrand.SEVEN ? "131386" : "006598", "測試門市",
                null)).orderNo();
        adminOrderService.confirm(orderNo, 0, customer.getId(), null);
        return orderNo;
    }

    /** 假的綠界：建立物流單成功（格式和簽章都和測試環境實際回傳的一樣） */
    private String stubCreateSuccess(String paymentNo, String validationNo) {
        Map<String, String> response = successResponse(paymentNo, validationNo);
        when(gateway.post(eq("/Express/Create"), anyMap())).thenReturn("1|" + join(response));
        return response.get("AllPayLogisticsID");
    }

    private Map<String, String> successResponse(String paymentNo, String validationNo) {
        Map<String, String> response = new HashMap<>();
        response.put("AllPayLogisticsID", String.valueOf(logisticsSeq++));
        response.put("BookingNote", "");
        response.put("CVSPaymentNo", paymentNo);
        response.put("CVSValidationNo", validationNo);
        response.put("GoodsAmount", "280");
        response.put("LogisticsSubType", validationNo.isEmpty() ? "FAMIC2C" : "UNIMARTC2C");
        response.put("LogisticsType", "CVS");
        response.put("MerchantID", MERCHANT_ID);
        response.put("MerchantTradeNo", "X");
        response.put("ReceiverName", "王小明");
        response.put("RtnCode", "300");
        response.put("RtnMsg", "訂單處理中(綠界已收到訂單資料)");
        response.put("UpdateStatusDate", "2026/11/14 15:18:11");
        response.put("CheckMacValue", EcpayCheckMacValue.compute(response, HASH_KEY, HASH_IV, Algorithm.MD5));
        return response;
    }

    private static String join(Map<String, String> fields) {
        return fields.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue()).collect(Collectors.joining("&"));
    }

    private void notify(String logisticsId, int code, String message, String time) throws Exception {
        Map<String, String> params = notificationParams(logisticsId, code, message, time);
        params.put("CheckMacValue", EcpayCheckMacValue.compute(params, HASH_KEY, HASH_IV, Algorithm.MD5));
        mockMvc.perform(notification(params)).andExpect(content().string("1|OK"));
    }

    private static Map<String, String> notificationParams(String logisticsId, int code, String message, String time) {
        Map<String, String> params = new HashMap<>();
        params.put("MerchantID", MERCHANT_ID);
        params.put("MerchantTradeNo", "X");
        params.put("RtnCode", String.valueOf(code));
        params.put("RtnMsg", message);
        params.put("AllPayLogisticsID", logisticsId);
        params.put("LogisticsType", "CVS");
        params.put("LogisticsSubType", "UNIMARTC2C");
        params.put("GoodsAmount", "280");
        params.put("UpdateStatusDate", time);
        params.put("ReceiverName", "王小明");
        params.put("ReceiverCellPhone", "0912345678");
        return params;
    }

    private static MockHttpServletRequestBuilder notification(Map<String, String> params) {
        MockHttpServletRequestBuilder request = post("/api/logistics/ecpay/notify")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED);
        params.forEach(request::param);
        return request;
    }

    private OrderStatus status(String orderNo) {
        return adminOrderService.get(orderNo).order().status();
    }

    private int stock() {
        return jdbc.queryForObject("SELECT stock FROM products WHERE id = ?", Integer.class, product.getId());
    }
}
