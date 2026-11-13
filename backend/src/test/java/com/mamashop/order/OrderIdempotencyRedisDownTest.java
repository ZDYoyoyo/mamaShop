package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.order.controller.OrderController;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.security.JwtService;
import com.mamashop.user.repository.UserRepository;
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

/**
 * Redis 連不到時（R02）：還是可以取得識別碼、下單（只是不檢查重複）。防重複是保護，不能讓所有人都不能下單。
 * 用沒有人在聽的 port 模擬 Redis 掛掉。
 */
@SpringBootTest(properties = "spring.data.redis.port=1")
@AutoConfigureMockMvc
class OrderIdempotencyRedisDownTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private OrderTestData data;
    private Product tote;

    @BeforeEach
    void setUp() {
        data = new OrderTestData(userRepository, categoryRepository, productRepository, jdbc);
        data.setUp();
        tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void ordersStillWork_withoutDuplicateCheck() throws Exception {
        String jwt = "Bearer " + jwtService.generateToken(data.user());

        String body = mockMvc.perform(post("/api/orders/checkout-token").header(HttpHeaders.AUTHORIZATION, jwt))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(body, "$.data.token");

        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, jwt)
                        .header(OrderController.IDEMPOTENCY_KEY, token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"items": [{"productId": %d, "quantity": 1}], "shippingMethod": "CVS_PICKUP",
                                 "recipientName": "王小明", "recipientPhone": "0912345678",
                                 "cvsBrand": "SEVEN", "cvsStoreCode": "123456", "cvsStoreName": "測試門市"}
                                """.formatted(tote.getId())))
                .andExpect(status().isCreated());

        assertThat(data.orderCount()).isEqualTo(1);
    }
}
