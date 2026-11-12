package com.mamashop.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 完整啟動應用程式（真的 Security filter、真的資料庫），驗證註冊、登入與權限規則。
 * @Transactional：每個測試結束後 rollback，不會在資料庫留下測試帳號。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Import(SecurityIntegrationTest.AdminTestController.class)
class SecurityIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JwtProperties jwtProperties;

    // ===== 註冊 =====

    @Test
    void register_success_storesHashedPassword_andNormalizesEmail() throws Exception {
        register("  New.User@Example.COM ", PASSWORD, "新會員", "0912345678")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("new.user@example.com"))
                .andExpect(jsonPath("$.data.role").value("USER"))
                .andExpect(jsonPath("$.data.passwordHash").doesNotExist());

        User saved = userRepository.findByEmail("new.user@example.com").orElseThrow();
        assertThat(saved.getPasswordHash()).startsWith("{bcrypt}").doesNotContain(PASSWORD);
    }

    @Test
    void register_duplicateEmail_ignoringCase_returns409() throws Exception {
        register("dup@example.com", PASSWORD, "A", null).andExpect(status().isCreated());

        register("DUP@example.com", PASSWORD, "B", null)
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
    }

    @Test
    void register_invalidInput_returns400WithFieldErrors() throws Exception {
        register("not-an-email", "short", "", "12345")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(4));
    }

    @Test
    void register_cannotChooseAdminRole() throws Exception {
        // 就算請求裡多塞 role，也會被忽略（DTO 沒有這個欄位）
        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "hacker@example.com", "password": "Passw0rd!", "name": "H", "role": "ADMIN"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.role").value("USER"));
    }

    // ===== 登入 =====

    @Test
    void login_success_returnsToken_thatWorksForMe() throws Exception {
        createUser("buyer@example.com", Role.USER, true);

        String token = loginAndGetToken("BUYER@example.com", PASSWORD);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.email").value("buyer@example.com"));
    }

    @Test
    void login_wrongPassword_andUnknownEmail_returnSameError() throws Exception {
        createUser("buyer@example.com", Role.USER, true);

        login("buyer@example.com", "WrongPass1")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
        login("nobody@example.com", PASSWORD)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("INVALID_CREDENTIALS"));
    }

    @Test
    void login_disabledAccount_returns401() throws Exception {
        createUser("disabled@example.com", Role.USER, false);

        login("disabled@example.com", PASSWORD).andExpect(status().isUnauthorized());
    }

    // ===== 需要登入的 API =====

    @Test
    void me_withoutToken_returns401Json() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("UNAUTHORIZED"));
    }

    @Test
    void me_withTamperedSignature_returns401() throws Exception {
        createUser("buyer@example.com", Role.USER, true);
        String token = loginAndGetToken("buyer@example.com", PASSWORD);
        // 換掉簽章「中間」的一個字元。
        // 注意：不能換最後一個字元——HS512 簽章 64 bytes 編成 86 個 Base64 字元，
        // 最後一個字元有 4 個 bit 是補位，A 換成 B 只改到補位，解碼後簽章完全相同（B11 時發現的偶發失敗）
        int signatureStart = token.lastIndexOf('.') + 1;
        int middle = signatureStart + (token.length() - signatureStart) / 2;
        char replacement = token.charAt(middle) == 'A' ? 'B' : 'A';
        String tampered = token.substring(0, middle) + replacement + token.substring(middle + 1);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withTamperedPayload_returns401() throws Exception {
        // 把 payload 的會員 id 改成別人（攻擊者最想做的事），簽章對不上就應該被拒絕
        User victim = createUser("victim@example.com", Role.ADMIN, true);
        createUser("buyer@example.com", Role.USER, true);
        String[] parts = loginAndGetToken("buyer@example.com", PASSWORD).split("\\.");
        String forgedPayload = java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(
                ("{\"sub\":\"" + victim.getId() + "\",\"exp\":4102444800}").getBytes(StandardCharsets.UTF_8));
        String forged = parts[0] + "." + forgedPayload + "." + parts[2];

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + forged))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_withExpiredToken_returns401() throws Exception {
        User user = createUser("buyer@example.com", Role.USER, true);
        Instant past = Instant.now().minusSeconds(3600);
        String expired = Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(past.minusSeconds(60)))
                .expiration(Date.from(past))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + expired))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void me_afterAccountDisabled_tokenStopsWorking() throws Exception {
        User user = createUser("buyer@example.com", Role.USER, true);
        String token = loginAndGetToken("buyer@example.com", PASSWORD);

        user.setEnabled(false);
        userRepository.saveAndFlush(user);

        mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    // ===== 後台權限 =====

    @Test
    void adminApi_withUserToken_returns403() throws Exception {
        createUser("buyer@example.com", Role.USER, true);
        String token = loginAndGetToken("buyer@example.com", PASSWORD);

        mockMvc.perform(get("/api/admin/__test/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("FORBIDDEN"));
    }

    @Test
    void adminApi_withAdminToken_returns200() throws Exception {
        createUser("boss@example.com", Role.ADMIN, true);
        String token = loginAndGetToken("boss@example.com", PASSWORD);

        mockMvc.perform(get("/api/admin/__test/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    void adminApi_withoutToken_returns401() throws Exception {
        mockMvc.perform(get("/api/admin/__test/ping")).andExpect(status().isUnauthorized());
    }

    // ===== 公開 API =====

    @Test
    void publicApis_workWithoutToken() throws Exception {
        mockMvc.perform(get("/api/products")).andExpect(status().isOk());
        mockMvc.perform(get("/api/categories")).andExpect(status().isOk());
        mockMvc.perform(get("/api/shipping-methods")).andExpect(status().isOk());
    }

    @Test
    void publicApis_stillWorkWithInvalidToken() throws Exception {
        mockMvc.perform(get("/api/products").header(HttpHeaders.AUTHORIZATION, "Bearer garbage"))
                .andExpect(status().isOk());
    }

    @Test
    void writeToPublicPath_requiresLogin() throws Exception {
        // 只開放 GET；同一路徑的 POST 不在公開清單內
        mockMvc.perform(post("/api/products").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }

    // ===== CORS =====

    @Test
    void cors_allowsLocalFrontend() throws Exception {
        mockMvc.perform(options("/api/products")
                        .header(HttpHeaders.ORIGIN, "http://localhost:5173")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:5173"));
    }

    @Test
    void cors_rejectsUnknownOrigin() throws Exception {
        mockMvc.perform(options("/api/products")
                        .header(HttpHeaders.ORIGIN, "https://evil.example.com")
                        .header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
                .andExpect(status().isForbidden());
    }

    // ===== helpers =====

    private User createUser(String email, Role role, boolean enabled) {
        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setName("測試");
        user.setRole(role);
        user.setEnabled(enabled);
        return userRepository.saveAndFlush(user);
    }

    private ResultActions register(String email, String password, String name, String phone) throws Exception {
        String json = """
                {"email": %s, "password": %s, "name": %s, "phone": %s}
                """.formatted(quote(email), quote(password), quote(name), quote(phone));
        return mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions login(String email, String password) throws Exception {
        String json = """
                {"email": %s, "password": %s}
                """.formatted(quote(email), quote(password));
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private String loginAndGetToken(String email, String password) throws Exception {
        String body = login(email, password)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return JsonPath.read(body, "$.data.accessToken");
    }

    private static String quote(String value) {
        return value == null ? "null" : "\"" + value + "\"";
    }

    /** 測試專用的後台 API，用來驗證 /api/admin/** 的權限規則 */
    @RestController
    static class AdminTestController {

        @GetMapping("/api/admin/__test/ping")
        Map<String, String> ping() {
            return Map.of("pong", "ok");
        }
    }
}
