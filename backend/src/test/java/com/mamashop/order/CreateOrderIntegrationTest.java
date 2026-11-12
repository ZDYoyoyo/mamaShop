package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.security.JwtService;
import com.mamashop.shipping.entity.ShippingMethod;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.shipping.repository.ShippingMethodRepository;
import com.mamashop.user.repository.UserRepository;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.Map;
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
 * 建立訂單（POST /api/orders）的整合測試：真的 Security、真的資料庫、真的交易。
 * 刻意不加 @Transactional，每次下單都會真的 COMMIT 或 ROLLBACK（原因見 OrderTestData）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class CreateOrderIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private ShippingMethodRepository shippingMethodRepository;
    @Autowired private JdbcTemplate jdbc;

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

    // ===== 成功 =====

    @Test
    void homeDelivery_success_calculatesAmounts_deductsStock_andWritesHistory() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        Product pouch = data.product("碎花零錢包", "320.00", 5, ProductStatus.ON_SALE);
        BigDecimal fee = feeOf(ShippingMethodCode.HOME_DELIVERY);
        BigDecimal expectedTotal = new BigDecimal("890.00").multiply(BigDecimal.valueOf(2))
                .add(new BigDecimal("320.00")).add(fee);

        String body = placeOrder("""
                {"items": [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 1}],
                 "shippingMethod": "HOME_DELIVERY",
                 "recipientName": " 王小明 ", "recipientPhone": "0912345678",
                 "shippingAddress": "台北市中正區重慶南路一段 122 號",
                 "cvsBrand": "SEVEN", "cvsStoreCode": "123456", "cvsStoreName": "不該被存"}
                """.formatted(tote.getId(), pouch.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.orderNo").value(matchesPattern("M\\d{16}")))
                .andExpect(jsonPath("$.data.status").value("PENDING_CONFIRM"))
                .andExpect(jsonPath("$.data.paymentMethod").value("COD"))
                .andExpect(jsonPath("$.data.paymentStatus").value("UNPAID"))
                .andExpect(jsonPath("$.data.recipientName").value("王小明"))
                .andExpect(jsonPath("$.data.itemsAmount").value(2100.00))
                .andExpect(jsonPath("$.data.shippingFee").value(fee.doubleValue()))
                .andExpect(jsonPath("$.data.totalAmount").value(expectedTotal.doubleValue()))
                .andExpect(jsonPath("$.data.items.length()").value(2))
                .andExpect(jsonPath("$.data.items[0].productName").value("帆布托特包"))
                .andExpect(jsonPath("$.data.items[0].subtotal").value(1780.00))
                // 宅配訂單不會存超商欄位
                .andExpect(jsonPath("$.data.cvsStoreName").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        assertThat(data.stockOf(tote)).isEqualTo(8);
        assertThat(data.stockOf(pouch)).isEqualTo(4);

        // 第一筆狀態紀錄：NULL → PENDING_CONFIRM，操作者是下單的顧客
        String orderNo = JsonPath.read(body, "$.data.orderNo");
        Map<String, Object> history = jdbc.queryForMap("""
                SELECT h.from_status, h.to_status, h.operator_id FROM order_status_histories h
                JOIN orders o ON o.id = h.order_id WHERE o.order_no = ?
                """, orderNo);
        assertThat(history.get("from_status")).isNull();
        assertThat(history.get("to_status")).isEqualTo("PENDING_CONFIRM");
        assertThat(((Number) history.get("operator_id")).longValue()).isEqualTo(data.user().getId());
    }

    @Test
    void cvsPickup_success_storesStoreInfo_andIgnoresAddress() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);

        placeOrder("""
                {"items": [{"productId": %d, "quantity": 1}],
                 "shippingMethod": "CVS_PICKUP",
                 "recipientName": "王小明", "recipientPhone": "0912345678",
                 "shippingAddress": "不該被存",
                 "cvsBrand": "FAMILY", "cvsStoreCode": "012345", "cvsStoreName": "全家測試店"}
                """.formatted(tote.getId()))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.shippingMethod").value("CVS_PICKUP"))
                .andExpect(jsonPath("$.data.cvsStoreName").value("全家測試店"))
                .andExpect(jsonPath("$.data.shippingAddress").doesNotExist())
                .andExpect(jsonPath("$.data.shippingFee").value(feeOf(ShippingMethodCode.CVS_PICKUP).doubleValue()));
    }

    @Test
    void duplicateItems_areMergedIntoOneLine() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 10, ProductStatus.ON_SALE);

        placeOrder(homeDelivery("""
                [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 3}]
                """.formatted(tote.getId(), tote.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].quantity").value(5));

        assertThat(data.stockOf(tote)).isEqualTo(5);
    }

    @Test
    void orderKeepsPriceSnapshot_afterProductPriceChanges() throws Exception {
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        placeOrder(homeDelivery(item(tote, 1))).andExpect(status().isCreated());

        jdbc.update("UPDATE products SET price = 999.00, name = '改名了' WHERE id = ?", tote.getId());

        Map<String, Object> item = jdbc.queryForMap(
                "SELECT product_name, unit_price FROM order_items WHERE product_id = ?", tote.getId());
        assertThat(item.get("product_name")).isEqualTo("帆布托特包");
        assertThat((BigDecimal) item.get("unit_price")).isEqualByComparingTo("890.00");
    }

    // ===== 失敗與回滾 =====

    @Test
    void insufficientStock_rollsBackTheWholeOrder() throws Exception {
        // 先建立的 id 比較小，會先扣：tote 扣成功之後，pouch 才失敗
        Product tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        Product pouch = data.product("碎花零錢包", "320.00", 1, ProductStatus.ON_SALE);

        placeOrder(homeDelivery("""
                [{"productId": %d, "quantity": 2}, {"productId": %d, "quantity": 5}]
                """.formatted(tote.getId(), pouch.getId())))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"))
                .andExpect(jsonPath("$.error.message").value(containsString("碎花零錢包")));

        // 重點：tote 雖然已經扣過 2 件，但整筆交易回滾，庫存回到 10
        assertThat(data.stockOf(tote)).isEqualTo(10);
        assertThat(data.stockOf(pouch)).isEqualTo(1);
        assertThat(data.orderCount()).isZero();
    }

    @Test
    void offSaleOrMissingProduct_returns409() throws Exception {
        Product offSale = data.product("下架商品", "100.00", 10, ProductStatus.OFF_SALE);

        placeOrder(homeDelivery(item(offSale, 1)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"));
        placeOrder(homeDelivery("""
                [{"productId": 999999999, "quantity": 1}]
                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("PRODUCT_UNAVAILABLE"));

        assertThat(data.stockOf(offSale)).isEqualTo(10);
    }

    @Test
    void disabledShippingMethod_returns409() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 10, ProductStatus.ON_SALE);
        ShippingMethod home = shippingMethodRepository.findByCode(ShippingMethodCode.HOME_DELIVERY).orElseThrow();
        home.setEnabled(false);
        shippingMethodRepository.save(home);
        try {
            placeOrder(homeDelivery(item(tote, 1)))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("SHIPPING_METHOD_UNAVAILABLE"));
            assertThat(data.stockOf(tote)).isEqualTo(10);
        } finally {
            home.setEnabled(true);
            shippingMethodRepository.save(home);
        }
    }

    // ===== 輸入驗證 =====

    @Test
    void homeDelivery_withoutAddress_returns400OnThatField() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 10, ProductStatus.ON_SALE);

        placeOrder("""
                {"items": [{"productId": %d, "quantity": 1}], "shippingMethod": "HOME_DELIVERY",
                 "recipientName": "王小明", "recipientPhone": "0912345678", "shippingAddress": "  "}
                """.formatted(tote.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("shippingAddress"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("宅配需填寫收件地址"));
    }

    @Test
    void cvsPickup_withoutStoreInfo_returns400ForEachMissingField() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 10, ProductStatus.ON_SALE);

        placeOrder("""
                {"items": [{"productId": %d, "quantity": 1}], "shippingMethod": "CVS_PICKUP",
                 "recipientName": "王小明", "recipientPhone": "0912345678"}
                """.formatted(tote.getId()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(3));
    }

    @Test
    void invalidItemsAndPhone_return400() throws Exception {
        placeOrder("""
                {"items": [], "shippingMethod": "HOME_DELIVERY",
                 "recipientName": "王小明", "recipientPhone": "12345", "shippingAddress": "台北"}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
        placeOrder(homeDelivery("""
                [{"productId": 1, "quantity": 0}]
                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("items[0].quantity"));
    }

    @Test
    void mergedQuantityOver99_returns400() throws Exception {
        Product tote = data.product("帆布托特包", "100.00", 500, ProductStatus.ON_SALE);

        placeOrder(homeDelivery("""
                [{"productId": %d, "quantity": 60}, {"productId": %d, "quantity": 50}]
                """.formatted(tote.getId(), tote.getId())))
                .andExpect(status().isBadRequest());
        assertThat(data.stockOf(tote)).isEqualTo(500);
    }

    @Test
    void notLoggedIn_returns401() throws Exception {
        mockMvc.perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON).content(homeDelivery("""
                        [{"productId": 1, "quantity": 1}]
                        """)))
                .andExpect(status().isUnauthorized());
    }

    // ===== helpers =====

    private ResultActions placeOrder(String json) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    private static String item(Product product, int quantity) {
        return "[{\"productId\": %d, \"quantity\": %d}]".formatted(product.getId(), quantity);
    }

    private static String homeDelivery(String itemsJson) {
        return """
                {"items": %s, "shippingMethod": "HOME_DELIVERY",
                 "recipientName": "王小明", "recipientPhone": "0912345678", "shippingAddress": "台北市"}
                """.formatted(itemsJson);
    }

    private BigDecimal feeOf(ShippingMethodCode code) {
        return shippingMethodRepository.findByCode(code).orElseThrow().getFee();
    }
}
