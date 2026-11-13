package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.service.OrderService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.security.JwtService;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.repository.UserRepository;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 後台概覽 API（A05）。
 *
 * <p>放在 order 套件是為了共用 {@link OrderTestData}（建立／清除訂單測試資料）。
 * 測試資料庫裡可能有其他資料，所以比較「建立測試資料前後的差」，而不是絕對數字。
 * 訂單狀態直接用 SQL 設定：這裡測的是「怎麼數」，狀態怎麼轉換在 AdminOrderIntegrationTest 測過。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AdminDashboardIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrderService orderService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private OrderTestData data;
    private String adminToken;
    private Product tote;

    @BeforeEach
    void setUp() {
        data = new OrderTestData(userRepository, categoryRepository, productRepository, jdbc);
        data.setUp();
        adminToken = jwtService.generateToken(data.adminUser("媽媽"));
        tote = data.product("帆布托特包", "890.00", 100, ProductStatus.ON_SALE);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void countsOrdersAndLowStockProducts() throws Exception {
        Map<String, Object> before = dashboard();

        String pending = placeOrder();
        setStatus(placeOrder(), "PROCESSING", null);
        setStatus(placeOrder(), "DELIVERED", null); // 超商取貨、到店未取件
        String oldReturned = placeOrder();
        setStatus(oldReturned, "RETURNED", "now()");
        jdbc.update("UPDATE orders SET created_at = now() - interval '3 days' WHERE order_no = ?", oldReturned); // 不是今天的訂單
        String longAgoReturned = placeOrder();
        setStatus(longAgoReturned, "RETURNED", "now() - interval '40 days'"); // 超過 30 天，不算
        jdbc.update("UPDATE orders SET created_at = now() - interval '50 days' WHERE order_no = ?", longAgoReturned);

        Product low = data.product("快賣完的包", "500.00", 2, ProductStatus.ON_SALE);
        data.product("下架的售完商品", "500.00", 0, ProductStatus.OFF_SALE); // 下架的不算
        data.product("庫存充足", "500.00", 6, ProductStatus.ON_SALE);

        Map<String, Object> after = dashboard();

        assertThat(delta(before, after, "todayOrders")).isEqualTo(3);
        assertThat(delta(before, after, "pendingConfirm")).isEqualTo(1);
        assertThat(delta(before, after, "awaitingShipment")).isEqualTo(1);
        assertThat(delta(before, after, "awaitingPickup")).isEqualTo(1);
        assertThat(delta(before, after, "returnedRecently")).isEqualTo(1);
        assertThat(delta(before, after, "lowStockCount")).isEqualTo(1);
        assertThat(after.get("lowStockThreshold")).isEqualTo(5);

        @SuppressWarnings("unchecked")
        List<Map<String, Object>> lowStock = (List<Map<String, Object>>) after.get("lowStockProducts");
        assertThat(lowStock).allSatisfy(p -> assertThat((Integer) p.get("stock")).isLessThanOrEqualTo(5));
        assertThat(lowStock).extracting(p -> (Integer) p.get("stock")).isSorted();
        if (((Number) after.get("lowStockCount")).intValue() <= 20) {
            assertThat(lowStock).extracting(p -> ((Number) p.get("id")).longValue()).contains(low.getId());
        }
        assertThat(pending).isNotBlank();
    }

    @Test
    void customerCannotSeeDashboard() throws Exception {
        String customerToken = jwtService.generateToken(data.user());

        mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + customerToken))
                .andExpect(status().isForbidden());
    }

    private Map<String, Object> dashboard() throws Exception {
        String body = mockMvc.perform(get("/api/admin/dashboard").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.data");
    }

    private static long delta(Map<String, Object> before, Map<String, Object> after, String key) {
        return ((Number) after.get(key)).longValue() - ((Number) before.get(key)).longValue();
    }

    private String placeOrder() {
        return orderService.createOrder(data.user().getId(), new CreateOrderRequest(
                List.of(new CreateOrderRequest.Item(tote.getId(), 1)), ShippingMethodCode.CVS_PICKUP,
                "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null))
                .orderNo();
    }

    /** returnedAt 是 SQL 運算式（例如 now()），null 表示不設定 */
    private void setStatus(String orderNo, String status, String returnedAt) {
        jdbc.update("UPDATE orders SET status = ?" + (returnedAt == null ? "" : ", returned_at = " + returnedAt)
                + " WHERE order_no = ?", status, orderNo);
    }
}
