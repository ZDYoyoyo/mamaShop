package com.mamashop.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.order.controller.OrderController;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.entity.CvsBrand;
import com.mamashop.order.service.CheckoutTokenService;
import com.mamashop.order.service.OrderSubmissionService;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import com.mamashop.security.JwtService;
import com.mamashop.shipping.entity.ShippingMethodCode;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
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
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 防重複下單（R02）：同一個結帳識別碼只會建立一筆訂單。需要 Redis（本機 docker compose up -d redis；CI 有 service）。
 */
@SpringBootTest
@AutoConfigureMockMvc
class OrderIdempotencyIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private OrderSubmissionService orderSubmissionService;
    @Autowired private CheckoutTokenService checkoutTokenService;
    @Autowired private UserRepository userRepository;
    @Autowired private CategoryRepository categoryRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private JdbcTemplate jdbc;

    private OrderTestData data;
    private Product tote;
    private String jwt;

    @BeforeEach
    void setUp() {
        data = new OrderTestData(userRepository, categoryRepository, productRepository, jdbc);
        data.setUp();
        tote = data.product("帆布托特包", "890.00", 10, ProductStatus.ON_SALE);
        jwt = jwtService.generateToken(data.user());
    }

    @AfterEach
    void cleanUp() {
        data.cleanUp();
    }

    @Test
    void checkoutToken_isIssued() throws Exception {
        mockMvc.perform(post("/api/orders/checkout-token").header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.token").isString())
                .andExpect(jsonPath("$.data.expiresInSeconds").value(1800));
    }

    @Test
    void sameToken_twice_createsOneOrder_andReturnsTheSameOrder() throws Exception {
        String token = checkoutTokenService.issue(data.user().getId());

        String first = orderNo(submit(token, 2).andExpect(status().isCreated()));
        // 網路重送、按了兩次：200 和同一筆訂單
        String second = orderNo(submit(token, 2).andExpect(status().isOk()));

        assertThat(second).isEqualTo(first);
        assertThat(data.orderCount()).isEqualTo(1);
        assertThat(data.stockOf(tote)).isEqualTo(8); // 只扣一次
    }

    @Test
    void sameToken_concurrently_createsExactlyOneOrder() throws Exception {
        String token = checkoutTokenService.issue(data.user().getId());
        int threads = 10;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> results = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            results.add(pool.submit(() -> {
                start.await(); // 全部準備好再同時送出
                try {
                    var result = orderSubmissionService.submit(data.user().getId(), token, request(1));
                    return result.replayed() ? "REPLAYED" : "CREATED";
                } catch (BusinessException e) {
                    return e.getErrorCode().name();
                }
            }));
        }
        start.countDown();
        List<String> outcomes = new ArrayList<>();
        for (Future<String> f : results) {
            outcomes.add(f.get());
        }
        pool.shutdown();

        // 只有一個請求真的建立訂單；其他的不是「處理中」就是拿到同一筆訂單
        assertThat(outcomes).filteredOn("CREATED"::equals).hasSize(1);
        assertThat(outcomes).allMatch(o -> List.of("CREATED", "REPLAYED", ErrorCode.ORDER_IN_PROGRESS.name()).contains(o));
        assertThat(data.orderCount()).isEqualTo(1);
        assertThat(data.stockOf(tote)).isEqualTo(9);
    }

    @Test
    void failedOrder_releasesToken_soTheSameTokenCanRetry() throws Exception {
        String token = checkoutTokenService.issue(data.user().getId());

        // 庫存不足：整筆失敗，token 還原
        submit(token, 99).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("INSUFFICIENT_STOCK"));
        // 顧客改數量後用同一個 token 再送：成功
        submit(token, 1).andExpect(status().isCreated());

        assertThat(data.orderCount()).isEqualTo(1);
    }

    @Test
    void missingToken_returns400() throws Exception {
        mockMvc.perform(post("/api/orders")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json(1)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("ORDER_TOKEN_REQUIRED"));
        assertThat(data.orderCount()).isZero();
    }

    @Test
    void unknownOrMalformedToken_isRejected() throws Exception {
        for (String token : List.of(UUID.randomUUID().toString(), "not-a-uuid", "x".repeat(500))) {
            submit(token, 1).andExpect(status().isConflict())
                    .andExpect(jsonPath("$.error.code").value("ORDER_TOKEN_INVALID"));
        }
        assertThat(data.orderCount()).isZero();
    }

    @Test
    void anotherUsersToken_isRejected() throws Exception {
        User other = data.otherUser();
        String othersToken = checkoutTokenService.issue(other.getId());

        submit(othersToken, 1).andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("ORDER_TOKEN_INVALID"));
        assertThat(data.orderCount()).isZero();
    }

    // ===== helpers =====

    private ResultActions submit(String token, int quantity) throws Exception {
        return mockMvc.perform(post("/api/orders")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + jwt)
                .header(OrderController.IDEMPOTENCY_KEY, token)
                .contentType(MediaType.APPLICATION_JSON)
                .content(json(quantity)));
    }

    private String json(int quantity) {
        return """
                {"items": [{"productId": %d, "quantity": %d}], "shippingMethod": "CVS_PICKUP",
                 "recipientName": "王小明", "recipientPhone": "0912345678",
                 "cvsBrand": "SEVEN", "cvsStoreCode": "123456", "cvsStoreName": "測試門市"}
                """.formatted(tote.getId(), quantity);
    }

    private CreateOrderRequest request(int quantity) {
        return new CreateOrderRequest(List.of(new CreateOrderRequest.Item(tote.getId(), quantity)),
                ShippingMethodCode.CVS_PICKUP, "王小明", "0912345678", null, CvsBrand.SEVEN, "123456", "測試門市", null);
    }

    private static String orderNo(ResultActions result) throws Exception {
        return JsonPath.read(result.andReturn().getResponse().getContentAsString(), "$.data.orderNo");
    }
}
