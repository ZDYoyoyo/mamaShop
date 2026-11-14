package com.mamashop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/**
 * 權限總表：列出每一支 API 應該給誰用，再用「未登入／顧客／管理員」三種身分逐一驗證。
 *
 * <p>這裡只驗證「有沒有被 Security 擋下」（401／403），不驗證業務結果，
 * 所以請求內容可以是空的（回 400、404 都代表有通過權限檢查、進到 Controller 了）。
 *
 * <p>{@link #everyApiIsListedInThisTable()}：掃描所有已註冊的 API，
 * 之後新增 API 卻忘了加進這張表，測試就會失敗——強迫每支新 API 都想清楚權限。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AccessControlTest {

    /** 誰可以使用 */
    enum Access {
        /** 不用登入 */
        PUBLIC,
        /** 登入即可（顧客、管理員） */
        LOGGED_IN,
        /** 只有管理員 */
        ADMIN
    }

    record Endpoint(HttpMethod method, String pattern, Access access) {

        String key() {
            return method.name() + " " + pattern;
        }

        /** 把 {id}、{orderNo} 換成假的值 */
        String samplePath() {
            return pattern.replace("{id}", "999999999").replace("{orderNo}", "M0000000000000000");
        }

        @Override
        public String toString() {
            return key() + " → " + access;
        }
    }

    /** 權限總表 */
    static Stream<Endpoint> endpoints() {
        return Stream.of(
                // ===== 公開 =====
                e("POST", "/api/auth/register", Access.PUBLIC),
                e("POST", "/api/auth/login", Access.PUBLIC),
                e("GET", "/api/categories", Access.PUBLIC),
                e("GET", "/api/products", Access.PUBLIC),
                e("GET", "/api/products/{id}", Access.PUBLIC),
                e("GET", "/api/shipping-methods", Access.PUBLIC),
                // ===== 登入即可 =====
                e("GET", "/api/users/me", Access.LOGGED_IN),
                e("POST", "/api/orders", Access.LOGGED_IN),
                e("POST", "/api/orders/checkout-token", Access.LOGGED_IN),
                e("GET", "/api/orders", Access.LOGGED_IN),
                e("GET", "/api/orders/{orderNo}", Access.LOGGED_IN),
                e("POST", "/api/orders/{orderNo}/cancel", Access.LOGGED_IN),
                // 線上付款（P01）：通知是綠界伺服器呼叫的，沒有 JWT（靠檢查碼驗證）
                e("POST", "/api/orders/{orderNo}/payment", Access.LOGGED_IN),
                e("GET", "/api/payments/options", Access.PUBLIC),
                e("POST", "/api/payments/ecpay/notify", Access.PUBLIC),
                // 超商電子地圖（P02）：選好門市後是綠界的地圖頁讓顧客瀏覽器 POST 回來，沒有 JWT
                e("GET", "/api/logistics/options", Access.PUBLIC),
                e("GET", "/api/logistics/cvs-map", Access.LOGGED_IN),
                e("POST", "/api/logistics/ecpay/cvs-map-reply", Access.PUBLIC),
                // ===== 後台 =====
                e("GET", "/api/admin/categories", Access.ADMIN),
                e("POST", "/api/admin/categories", Access.ADMIN),
                e("PUT", "/api/admin/categories/{id}", Access.ADMIN),
                e("DELETE", "/api/admin/categories/{id}", Access.ADMIN),
                e("GET", "/api/admin/products", Access.ADMIN),
                e("GET", "/api/admin/products/{id}", Access.ADMIN),
                e("POST", "/api/admin/products", Access.ADMIN),
                e("PUT", "/api/admin/products/{id}", Access.ADMIN),
                e("PATCH", "/api/admin/products/{id}/status", Access.ADMIN),
                e("PATCH", "/api/admin/products/{id}/stock", Access.ADMIN),
                e("POST", "/api/admin/products/{id}/image", Access.ADMIN),
                e("GET", "/api/admin/shipping-methods", Access.ADMIN),
                e("PUT", "/api/admin/shipping-methods/{id}", Access.ADMIN),
                e("GET", "/api/admin/dashboard", Access.ADMIN),
                e("GET", "/api/admin/orders", Access.ADMIN),
                e("GET", "/api/admin/orders/{orderNo}", Access.ADMIN),
                e("POST", "/api/admin/orders/{orderNo}/confirm", Access.ADMIN),
                e("POST", "/api/admin/orders/{orderNo}/ship", Access.ADMIN),
                e("POST", "/api/admin/orders/{orderNo}/deliver", Access.ADMIN),
                e("POST", "/api/admin/orders/{orderNo}/complete", Access.ADMIN),
                e("POST", "/api/admin/orders/{orderNo}/cancel", Access.ADMIN),
                e("POST", "/api/admin/orders/{orderNo}/return", Access.ADMIN),
                e("GET", "/api/admin/admins", Access.ADMIN),
                e("POST", "/api/admin/admins", Access.ADMIN),
                e("PATCH", "/api/admin/admins/{id}/enabled", Access.ADMIN),
                e("PUT", "/api/admin/admins/{id}/password", Access.ADMIN));
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private JwtService jwtService;

    @Autowired
    @Qualifier("requestMappingHandlerMapping")
    private RequestMappingHandlerMapping handlerMapping;

    private String customerToken;
    private String adminToken;

    @BeforeEach
    void setUp() {
        customerToken = jwtService.generateToken(user("acl-customer@example.com", Role.USER));
        adminToken = jwtService.generateToken(user("acl-admin@example.com", Role.ADMIN));
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void anonymous(Endpoint endpoint) throws Exception {
        int status = call(endpoint, null);
        if (endpoint.access() == Access.PUBLIC) {
            assertThat(status).as("公開 API 不該要求登入").isNotIn(401, 403);
        } else {
            assertThat(status).as("未登入應回 401").isEqualTo(401);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void customer(Endpoint endpoint) throws Exception {
        int status = call(endpoint, customerToken);
        if (endpoint.access() == Access.ADMIN) {
            assertThat(status).as("顧客呼叫後台 API 應回 403").isEqualTo(403);
        } else {
            assertThat(status).as("顧客應該可以使用").isNotIn(401, 403);
        }
    }

    @ParameterizedTest(name = "{0}")
    @MethodSource("endpoints")
    void admin(Endpoint endpoint) throws Exception {
        assertThat(call(endpoint, adminToken)).as("管理員應該可以使用").isNotIn(401, 403);
    }

    @Test
    void everyApiIsListedInThisTable() {
        Set<String> registered = handlerMapping.getHandlerMethods().keySet().stream()
                .flatMap(info -> info.getPatternValues().stream()
                        .flatMap(pattern -> info.getMethodsCondition().getMethods().stream()
                                .map(method -> method.name() + " " + pattern)))
                // 排除 Spring Boot 內建的 /error，以及其他測試類別裡的測試專用 Controller
                .filter(key -> !key.endsWith(" /error") && !key.contains("__test"))
                // 排除 API 文件（springdoc）：是否存在取決於 API_DOCS_ENABLED，由 ApiDocsTest 驗證
                .filter(key -> !key.contains(" /v3/api-docs") && !key.contains(" /swagger-ui"))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> listed = endpoints().map(Endpoint::key).collect(Collectors.toCollection(TreeSet::new));

        assertThat(registered)
                .as("有 API 沒有列在權限總表（AccessControlTest.endpoints），請加上並決定誰可以使用")
                .isSubsetOf(listed);
        assertThat(listed).as("權限總表裡有已經不存在的 API").isSubsetOf(registered);
    }

    // ===== helpers =====

    private int call(Endpoint endpoint, String token) throws Exception {
        MockHttpServletRequestBuilder builder = request(endpoint.method(), endpoint.samplePath());
        if (endpoint.method() != HttpMethod.GET && endpoint.method() != HttpMethod.DELETE) {
            builder.contentType(MediaType.APPLICATION_JSON).content("{}");
        }
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mockMvc.perform(builder).andReturn().getResponse().getStatus();
    }

    private User user(String email, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash("not-used");
        u.setName("權限測試");
        u.setRole(role);
        return userRepository.save(u);
    }

    private static Endpoint e(String method, String pattern, Access access) {
        return new Endpoint(HttpMethod.valueOf(method), pattern, access);
    }
}
