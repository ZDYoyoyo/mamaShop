package com.mamashop.product;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.shipping.repository.ShippingMethodRepository;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;

/**
 * 後台分類、商品、運送方式管理的整合測試。
 * @WithMockUser(roles = "ADMIN")：直接以 ADMIN 身分執行，不用每個測試都先登入拿 token
 * （JWT 本身在 SecurityIntegrationTest 測過）。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@WithMockUser(roles = "ADMIN")
class AdminCatalogIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ShippingMethodRepository shippingMethodRepository;

    // ===== 權限 =====

    @Test
    @WithMockUser(roles = "USER")
    void customer_cannotAccessAdminApis() throws Exception {
        mockMvc.perform(get("/api/admin/products")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/categories")).andExpect(status().isForbidden());
        mockMvc.perform(get("/api/admin/shipping-methods")).andExpect(status().isForbidden());
    }

    // ===== 分類 =====

    @Test
    void category_createUpdateDelete() throws Exception {
        long id = createCategory("測試分類A", 5);

        send(put("/api/admin/categories/" + id), """
                {"name": "測試分類B", "sortOrder": 1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("測試分類B"))
                .andExpect(jsonPath("$.data.sortOrder").value(1));

        mockMvc.perform(delete("/api/admin/categories/" + id)).andExpect(status().isOk());
        mockMvc.perform(delete("/api/admin/categories/" + id)).andExpect(status().isNotFound());
    }

    @Test
    void category_duplicateName_returns409() throws Exception {
        createCategory("測試分類A", 0);
        send(post("/api/admin/categories"), """
                {"name": "  測試分類A  "}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_NAME_EXISTS"));
    }

    @Test
    void category_withProducts_cannotBeDeleted() throws Exception {
        long categoryId = createCategory("測試分類A", 0);
        createProduct(categoryId, "測試商品", "100", 5);

        mockMvc.perform(delete("/api/admin/categories/" + categoryId))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CATEGORY_HAS_PRODUCTS"));
    }

    // ===== 商品 =====

    @Test
    void product_createdOffSale_thenPutOnSale_becomesVisibleToCustomers() throws Exception {
        long categoryId = createCategory("測試分類A", 0);
        long productId = createProduct(categoryId, "帆布包", "890.00", 10);

        mockMvc.perform(get("/api/admin/products/" + productId))
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"))
                .andExpect(jsonPath("$.data.categoryName").value("測試分類A"));
        // 剛建立是下架狀態，前台看不到
        mockMvc.perform(get("/api/products/" + productId)).andExpect(status().isNotFound());

        send(patch("/api/admin/products/" + productId + "/status"), """
                {"status": "ON_SALE"}
                """).andExpect(status().isOk());

        mockMvc.perform(get("/api/products/" + productId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("帆布包"));
    }

    @Test
    void product_update_changesBasicInfoButNotStock() throws Exception {
        long categoryA = createCategory("測試分類A", 0);
        long categoryB = createCategory("測試分類B", 0);
        long productId = createProduct(categoryA, "舊名稱", "100", 7);

        send(put("/api/admin/products/" + productId), """
                {"categoryId": %d, "name": "新名稱", "description": "新描述", "price": 250.50, "stock": 999}
                """.formatted(categoryB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("新名稱"))
                .andExpect(jsonPath("$.data.categoryName").value("測試分類B"))
                .andExpect(jsonPath("$.data.price").value(250.50))
                // 請求裡的 stock 會被忽略（DTO 沒有這個欄位）
                .andExpect(jsonPath("$.data.stock").value(7));
    }

    @Test
    void product_adjustStock_byDelta() throws Exception {
        long productId = createProduct(createCategory("測試分類A", 0), "測試商品", "100", 5);

        send(patch("/api/admin/products/" + productId + "/stock"), """
                {"delta": 3}
                """).andExpect(jsonPath("$.data.stock").value(8));
        send(patch("/api/admin/products/" + productId + "/stock"), """
                {"delta": -8}
                """).andExpect(jsonPath("$.data.stock").value(0));
        send(patch("/api/admin/products/" + productId + "/stock"), """
                {"delta": -1}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("STOCK_ADJUSTMENT_INVALID"));
    }

    @Test
    void product_adjustStock_unknownProduct_returns404() throws Exception {
        send(patch("/api/admin/products/999999999/stock"), """
                {"delta": 1}
                """).andExpect(status().isNotFound());
    }

    @Test
    void product_invalidInput_returns400() throws Exception {
        send(post("/api/admin/products"), """
                {"categoryId": null, "name": "", "price": -1, "stock": -5}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(4));
        // 小數超過 2 位
        send(post("/api/admin/products"), """
                {"categoryId": 1, "name": "x", "price": 10.999, "stock": 1}
                """)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("price"));
    }

    @Test
    void product_unknownCategory_returns404() throws Exception {
        send(post("/api/admin/products"), """
                {"categoryId": 999999999, "name": "x", "price": 1, "stock": 1}
                """).andExpect(status().isNotFound());
    }

    @Test
    void adminList_includesOffSaleProducts_andFiltersByStatus() throws Exception {
        long categoryId = createCategory("測試分類A", 0);
        createProduct(categoryId, "下架中", "100", 1);

        mockMvc.perform(get("/api/admin/products")
                        .param("categoryId", String.valueOf(categoryId)).param("status", "OFF_SALE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.items[0].name").value("下架中"));
        mockMvc.perform(get("/api/admin/products")
                        .param("categoryId", String.valueOf(categoryId)).param("status", "ON_SALE"))
                .andExpect(jsonPath("$.data.totalElements").value(0));
    }

    // ===== 運送方式 =====

    @Test
    void shippingMethod_updateFee() throws Exception {
        long id = shippingMethodId(ShippingMethodCode.HOME_DELIVERY);

        send(put("/api/admin/shipping-methods/" + id), """
                {"name": "宅配到府", "fee": 120, "enabled": true, "sortOrder": 1}
                """)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.fee").value(120))
                .andExpect(jsonPath("$.data.code").value("HOME_DELIVERY"));
    }

    @Test
    void shippingMethod_cannotDisableTheLastEnabledOne() throws Exception {
        long home = shippingMethodId(ShippingMethodCode.HOME_DELIVERY);
        long cvs = shippingMethodId(ShippingMethodCode.CVS_PICKUP);

        send(put("/api/admin/shipping-methods/" + home), """
                {"name": "宅配", "fee": 100, "enabled": false, "sortOrder": 1}
                """).andExpect(status().isOk());
        send(put("/api/admin/shipping-methods/" + cvs), """
                {"name": "超商取貨", "fee": 60, "enabled": false, "sortOrder": 2}
                """)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("LAST_SHIPPING_METHOD"));
        // 停用的不會出現在前台
        mockMvc.perform(get("/api/shipping-methods"))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].code").value("CVS_PICKUP"));
    }

    // ===== helpers =====

    private ResultActions send(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder builder,
                               String json) throws Exception {
        return mockMvc.perform(builder.contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private long createCategory(String name, int sortOrder) throws Exception {
        String body = send(post("/api/admin/categories"), """
                {"name": "%s", "sortOrder": %d}
                """.formatted(name, sortOrder))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long createProduct(long categoryId, String name, String price, int stock) throws Exception {
        String body = send(post("/api/admin/products"), """
                {"categoryId": %d, "name": "%s", "description": "說明", "price": %s, "stock": %d}
                """.formatted(categoryId, name, price, stock))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.status").value("OFF_SALE"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return ((Number) JsonPath.read(body, "$.data.id")).longValue();
    }

    private long shippingMethodId(ShippingMethodCode code) {
        return shippingMethodRepository.findAll().stream()
                .filter(m -> m.getCode() == code).findFirst().orElseThrow().getId();
    }
}
