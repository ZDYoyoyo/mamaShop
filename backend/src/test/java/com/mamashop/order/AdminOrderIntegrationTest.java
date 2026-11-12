package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.security.JwtService;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 後台訂單管理（B10）：完整流程、狀態機、版本號、庫存歸還、狀態紀錄。
 * 用真的 JWT 登入兩位管理員（媽媽、爸爸），驗證紀錄裡的操作者名字。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminOrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrderService orderService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private OrderTestData data;
    private String momToken;
    private String dadToken;
    private Product tote;

    @BeforeEach
    void setUp() {
        data = new OrderTestData(userRepository, categoryRepository, productRepository, jdbc);
        data.setUp();
        momToken = jwtService.generateToken(data.adminUser("媽媽"));
        dadToken = jwtService.generateToken(data.adminUser("爸爸"));
        tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    // ===== 完整流程 =====

    @Test
    void fullFlow_confirmShipDeliverComplete() throws Exception {
        String orderNo = placeOrder(data.user(), 2);

        long v = version(action(momToken, orderNo, "confirm", "{\"version\": 0, \"note\": \"已電話確認\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.order.confirmedAt").exists()));

        v = version(action(dadToken, orderNo, "ship", "{\"version\": %d, \"trackingNo\": \" 9988776655 \"}".formatted(v))
                .andExpect(jsonPath("$.data.order.status").value("SHIPPED"))
                .andExpect(jsonPath("$.data.order.trackingNo").value("9988776655")));

        v = version(action(momToken, orderNo, "deliver", "{\"version\": %d}".formatted(v))
                .andExpect(jsonPath("$.data.order.status").value("DELIVERED")));

        action(momToken, orderNo, "complete", "{\"version\": %d}".formatted(v))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("COMPLETED"))
                .andExpect(jsonPath("$.data.order.paymentStatus").value("PAID"))
                .andExpect(jsonPath("$.data.order.paidAt").exists())
                // 狀態紀錄：顧客下單 + 4 個後台操作，記下是誰做的
                .andExpect(jsonPath("$.data.histories.length()").value(5))
                .andExpect(jsonPath("$.data.histories[0].toStatus").value("PENDING_CONFIRM"))
                .andExpect(jsonPath("$.data.histories[0].operatorRole").value("USER"))
                .andExpect(jsonPath("$.data.histories[1].operatorName").value("媽媽"))
                .andExpect(jsonPath("$.data.histories[1].note").value("已電話確認"))
                .andExpect(jsonPath("$.data.histories[2].operatorName").value("爸爸"))
                .andExpect(jsonPath("$.data.histories[2].toStatus").value("SHIPPED"));

        // 完成的訂單不會歸還庫存
        assertThat(data.stockOf(tote)).isEqualTo(8);
    }

    // ===== 狀態機 =====

    @Test
    void cannotSkipSteps() throws Exception {
        String orderNo = placeOrder(data.user(), 1);

        // 還沒確認就出貨
        action(momToken, orderNo, "ship", "{\"version\": 0, \"trackingNo\": \"123\"}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_STATUS"))
                .andExpect(jsonPath("$.error.message").value("訂單目前是「待確認」，不能改為「已出貨」"));
    }

    // ===== 版本號（多位管理員） =====

    @Test
    void staleVersion_isRejected_andNothingChanges() throws Exception {
        String orderNo = placeOrder(data.user(), 2);
        // 媽媽和爸爸都打開了這張訂單（版本 0）。爸爸先按了確認 → 版本變成 1
        action(dadToken, orderNo, "confirm", "{\"version\": 0}").andExpect(status().isOk());

        // 媽媽的畫面還是版本 0，按下取消 → 被擋下，不會在不知情的情況下取消已確認的訂單
        action(momToken, orderNo, "cancel", "{\"version\": 0}")
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));

        mockMvc.perform(get("/api/admin/orders/" + orderNo).header(HttpHeaders.AUTHORIZATION, "Bearer " + momToken))
                .andExpect(jsonPath("$.data.order.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.version").value(1));
        assertThat(data.stockOf(tote)).isEqualTo(8);
    }

    // ===== 取消 =====

    @Test
    void adminCanCancelProcessingOrder_andStockIsRestored() throws Exception {
        String orderNo = placeOrder(data.user(), 3);
        long v = version(action(momToken, orderNo, "confirm", "{\"version\": 0}"));

        action(momToken, orderNo, "cancel", "{\"version\": %d, \"note\": \"顧客來電取消\"}".formatted(v))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("CANCELLED"));

        assertThat(data.stockOf(tote)).isEqualTo(10);
    }

    @Test
    void cannotCancelAfterShipping() throws Exception {
        String orderNo = placeOrder(data.user(), 1);
        long v = version(action(momToken, orderNo, "confirm", "{\"version\": 0}"));
        v = version(action(momToken, orderNo, "ship", "{\"version\": %d, \"trackingNo\": \"123\"}".formatted(v)));

        action(momToken, orderNo, "cancel", "{\"version\": %d}".formatted(v))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_STATUS"));
        assertThat(data.stockOf(tote)).isEqualTo(9);
    }

    // ===== 退回 =====

    @Test
    void returned_withRestock_restoresStock() throws Exception {
        String orderNo = shippedOrder(4);

        action(momToken, orderNo, "return", "{\"version\": 2, \"restock\": true, \"note\": \"超商未取件\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.order.status").value("RETURNED"))
                .andExpect(jsonPath("$.data.order.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.histories[3].note").value("已歸還庫存；超商未取件"));

        assertThat(data.stockOf(tote)).isEqualTo(10);
    }

    @Test
    void returned_withoutRestock_keepsStock() throws Exception {
        String orderNo = shippedOrder(4);

        action(momToken, orderNo, "return", "{\"version\": 2, \"restock\": false, \"note\": \"包裝破損\"}")
                .andExpect(status().isOk());

        assertThat(data.stockOf(tote)).isEqualTo(6);
    }

    // ===== 輸入驗證與權限 =====

    @Test
    void ship_requiresTrackingNo_andVersion() throws Exception {
        String orderNo = placeOrder(data.user(), 1);

        action(momToken, orderNo, "ship", "{\"trackingNo\": \"  \"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
    }

    @Test
    void customer_cannotUseAdminApis() throws Exception {
        String customerToken = jwtService.generateToken(data.user());
        String orderNo = placeOrder(data.user(), 1);

        action(customerToken, orderNo, "confirm", "{\"version\": 0}").andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/orders").header(HttpHeaders.AUTHORIZATION, "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    @Test
    void unknownOrder_returns404() throws Exception {
        action(momToken, "M0000000000000000", "confirm", "{\"version\": 0}").andExpect(status().isNotFound());
    }

    // ===== 列表與詳情 =====

    @Test
    void list_showsAllCustomersOrders_andFiltersByStatus() throws Exception {
        User other = data.otherUser();
        String mine = placeOrder(data.user(), 1);
        placeOrder(other, 1);
        action(momToken, mine, "confirm", "{\"version\": 0}").andExpect(status().isOk());

        String body = mockMvc.perform(get("/api/admin/orders").param("status", "PENDING_CONFIRM").param("size", "100")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + momToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> statuses = JsonPath.read(body, "$.data.items[*].status");
        List<String> customers = JsonPath.read(body, "$.data.items[*].customerName");
        List<String> orderNos = JsonPath.read(body, "$.data.items[*].orderNo");
        assertThat(statuses).containsOnly("PENDING_CONFIRM");
        assertThat(orderNos).doesNotContain(mine);   // 已確認，不在「待確認」裡
        assertThat(customers).contains("下單測試");   // 看得到其他顧客的訂單
    }

    @Test
    void detail_includesCustomerInfo() throws Exception {
        String orderNo = placeOrder(data.user(), 1);

        mockMvc.perform(get("/api/admin/orders/" + orderNo).header(HttpHeaders.AUTHORIZATION, "Bearer " + momToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.customer.email").value(data.user().getEmail()))
                .andExpect(jsonPath("$.data.order.recipientName").value("王小明"))
                .andExpect(jsonPath("$.data.version").value(0));
    }

    // ===== helpers =====

    private ResultActions action(String token, String orderNo, String action, String json) throws Exception {
        return mockMvc.perform(post("/api/admin/orders/" + orderNo + "/" + action)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    /** 從回應取出新的版本號，下一個操作要帶它 */
    private static long version(ResultActions result) throws Exception {
        String body = result.andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.data.version")).longValue();
    }

    private String placeOrder(User customer, int quantity) {
        return orderService.createOrder(customer.getId(), new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(tote.getId(), quantity)), ShippingMethodCode.CVS_PICKUP,
                "王小明", "0912345678", null, com.mamashop.order.entity.CvsBrand.SEVEN, "123456", "測試門市", null))
                .orderNo();
    }

    /** 已出貨的訂單（版本 2） */
    private String shippedOrder(int quantity) throws Exception {
        String orderNo = placeOrder(data.user(), quantity);
        action(momToken, orderNo, "confirm", "{\"version\": 0}").andExpect(status().isOk());
        action(momToken, orderNo, "ship", "{\"version\": 1, \"trackingNo\": \"123\"}").andExpect(status().isOk());
        return orderNo;
    }
}
