package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import jakarta.persistence.EntityManagerFactory;
import java.util.List;
import java.util.Map;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 顧客訂單功能（B09）：我的訂單、訂單詳情、取消訂單。
 * 重點：只能看到、取消自己的訂單；取消時歸還庫存。
 * 開啟 Hibernate statistics 以計算 SQL 數量，確認沒有 N+1。
 */
@SpringBootTest(properties = "spring.jpa.properties.hibernate.generate_statistics=true")
@AutoConfigureMockMvc
class CustomerOrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private OrderService orderService;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private EntityManagerFactory entityManagerFactory;

    private OrderTestData data;
    private String token;

    @BeforeEach
    void setUp() {
        data = new OrderTestData(userRepository, categoryRepository, productRepository, jdbc);
        data.setUp();
        token = jwtService.generateToken(data.user());
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    // ===== 我的訂單 =====

    @Test
    void list_showsOnlyMyOrders_newestFirst() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 50, ProductStatus.ON_SALE);
        Product pouch = data.product("零錢包", "50.00", 50, ProductStatus.ON_SALE);
        placeOrder(data.user(), tote, 1);
        placeOrder(data.user(), tote, 2);
        String newest = placeOrder(data.user(), pouch, 3);
        placeOrder(data.otherUser(), tote, 1); // 別人的訂單

        mockMvc.perform(authed(get("/api/orders").param("size", "2")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(3))
                .andExpect(jsonPath("$.data.totalPages").value(2))
                .andExpect(jsonPath("$.data.items[0].orderNo").value(newest))
                .andExpect(jsonPath("$.data.items[0].firstProductName").value("零錢包"))
                .andExpect(jsonPath("$.data.items[0].totalQuantity").value(3))
                .andExpect(jsonPath("$.data.items[0].status").value("PENDING_CONFIRM"));
    }

    @Test
    void list_loadsItemsWithoutNPlusOne() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 50, ProductStatus.ON_SALE);
        for (int i = 0; i < 5; i++) {
            placeOrder(data.user(), tote, 1);
        }
        Statistics stats = entityManagerFactory.unwrap(SessionFactory.class).getStatistics();
        stats.clear();

        mockMvc.perform(authed(get("/api/orders"))).andExpect(status().isOk());

        // 1 查會員（JWT filter）+ 1 查訂單 + 1 批次查 5 筆訂單的明細 = 3
        // （第一頁沒滿時 Spring Data 不會再查總筆數；資料多於一頁時會再多 1 個 count）
        // 實測關掉 default_batch_fetch_size 會變成 7 個：明細一筆訂單查一次（N+1）
        assertThat(stats.getPrepareStatementCount()).isLessThanOrEqualTo(4);
    }

    // ===== 訂單詳情 =====

    @Test
    void detail_ownOrder_returnsItems() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        String orderNo = placeOrder(data.user(), tote, 2);

        mockMvc.perform(authed(get("/api/orders/" + orderNo)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderNo))
                .andExpect(jsonPath("$.data.items[0].productName").value("帆布托特包"))
                .andExpect(jsonPath("$.data.items[0].quantity").value(2))
                .andExpect(jsonPath("$.data.createdAt").exists())
                .andExpect(jsonPath("$.data.confirmedAt").doesNotExist());
    }

    @Test
    void detail_otherUsersOrder_returns404NotForbidden() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        String othersOrder = placeOrder(data.otherUser(), tote, 1);

        // 回 404 而不是 403：不讓人知道「這個訂單編號存在」
        mockMvc.perform(authed(get("/api/orders/" + othersOrder)))
                .andExpect(status().isNotFound());
        mockMvc.perform(authed(get("/api/orders/M0000000000000000")))
                .andExpect(status().isNotFound());
    }

    @Test
    void notLoggedIn_returns401() throws Exception {
        mockMvc.perform(get("/api/orders")).andExpect(status().isUnauthorized());
    }

    // ===== 取消訂單 =====

    @Test
    void cancel_pendingOrder_restoresStock_andWritesHistory() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        Product pouch = data.product("零錢包", "320.00", 5, ProductStatus.ON_SALE);
        String orderNo = placeOrder(data.user(), List.of(
                new CreateOrderRequest.Item(tote.getId(), 3), new CreateOrderRequest.Item(pouch.getId(), 2)));
        assertThat(data.stockOf(tote)).isEqualTo(7);

        mockMvc.perform(authed(post("/api/orders/" + orderNo + "/cancel")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"))
                .andExpect(jsonPath("$.data.cancelledAt").exists());

        assertThat(data.stockOf(tote)).isEqualTo(10);
        assertThat(data.stockOf(pouch)).isEqualTo(5);

        Map<String, Object> history = jdbc.queryForMap("""
                SELECT h.from_status, h.to_status, h.operator_id, h.note FROM order_status_histories h
                JOIN orders o ON o.id = h.order_id WHERE o.order_no = ? ORDER BY h.id DESC LIMIT 1
                """, orderNo);
        assertThat(history.get("from_status")).isEqualTo("PENDING_CONFIRM");
        assertThat(history.get("to_status")).isEqualTo("CANCELLED");
        assertThat(((Number) history.get("operator_id")).longValue()).isEqualTo(data.user().getId());
    }

    @Test
    void cancel_twice_secondTimeIsRejected_andStockRestoredOnlyOnce() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        String orderNo = placeOrder(data.user(), tote, 4);

        mockMvc.perform(authed(post("/api/orders/" + orderNo + "/cancel"))).andExpect(status().isOk());
        mockMvc.perform(authed(post("/api/orders/" + orderNo + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INVALID_ORDER_STATUS"));

        assertThat(data.stockOf(tote)).isEqualTo(10);
    }

    @Test
    void cancel_afterSellerConfirmed_isRejected() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        String orderNo = placeOrder(data.user(), tote, 2);
        // 模擬家人已在後台確認（B10 才有這個 API）
        jdbc.update("UPDATE orders SET status = 'PROCESSING' WHERE order_no = ?", orderNo);

        mockMvc.perform(authed(post("/api/orders/" + orderNo + "/cancel")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_CANNOT_CANCEL"));

        assertThat(data.stockOf(tote)).isEqualTo(8);
    }

    @Test
    void cancel_otherUsersOrder_returns404_andNothingChanges() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        String othersOrder = placeOrder(data.otherUser(), tote, 2);

        mockMvc.perform(authed(post("/api/orders/" + othersOrder + "/cancel")))
                .andExpect(status().isNotFound());

        assertThat(data.stockOf(tote)).isEqualTo(8);
        assertThat(jdbc.queryForObject("SELECT status FROM orders WHERE order_no = ?", String.class, othersOrder))
                .isEqualTo("PENDING_CONFIRM");
    }

    @Test
    void cancel_restoresStock_evenIfProductWasTakenOffSale() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        String orderNo = placeOrder(data.user(), tote, 3);
        jdbc.update("UPDATE products SET status = 'OFF_SALE' WHERE id = ?", tote.getId());

        mockMvc.perform(authed(post("/api/orders/" + orderNo + "/cancel"))).andExpect(status().isOk());

        assertThat(data.stockOf(tote)).isEqualTo(10);
    }

    // ===== helpers =====

    private MockHttpServletRequestBuilder authed(MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private String placeOrder(User user, Product product, int quantity) {
        return placeOrder(user, List.of(new CreateOrderRequest.Item(product.getId(), quantity)));
    }

    private String placeOrder(User user, List<CreateOrderRequest.Item> items) {
        return orderService.createOrder(user.getId(), new CreateOrderRequest(items, ShippingMethodCode.HOME_DELIVERY,
                "王小明", "0912345678", "台北市", null, null, null, null)).orderNo();
    }
}
