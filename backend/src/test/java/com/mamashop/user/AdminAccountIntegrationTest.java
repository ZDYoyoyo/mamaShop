package com.mamashop.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import com.mamashop.security.JwtProperties;
import com.mamashop.security.JwtService;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理員帳號管理（B14）。
 * 重點：停用、重設密碼「立即」讓既有的 token 失效；不能停用自己。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class AdminAccountIntegrationTest {

    private static final String PASSWORD = "Passw0rd!";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private JwtService jwtService;
    @Autowired private JwtProperties jwtProperties;

    private User mom;
    private User dad;
    private String momToken;

    @BeforeEach
    void setUp() {
        mom = user("mom@example.com", "媽媽", Role.ADMIN);
        dad = user("dad@example.com", "爸爸", Role.ADMIN);
        momToken = jwtService.generateToken(mom);
    }

    // ===== 列表與新增 =====

    @Test
    void list_showsOnlyAdmins() throws Exception {
        user("customer@example.com", "顧客", Role.USER);

        String body = mockMvc.perform(as(momToken, get("/api/admin/admins")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].passwordHash").doesNotExist())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

        List<String> emails = JsonPath.read(body, "$.data[*].email");
        assertThat(emails).contains("mom@example.com", "dad@example.com").doesNotContain("customer@example.com");
    }

    @Test
    void create_newAdminCanLogIn() throws Exception {
        mockMvc.perform(as(momToken, post("/api/admin/admins")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": " Sister@Example.com ", "password": "Sister123!", "name": "姊姊"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.data.email").value("sister@example.com"))
                .andExpect(jsonPath("$.data.enabled").value(true));

        assertThat(userRepository.findByEmail("sister@example.com").orElseThrow().getRole()).isEqualTo(Role.ADMIN);
        login("sister@example.com", "Sister123!").andExpect(status().isOk());
    }

    @Test
    void create_duplicateEmail_orWeakPassword_isRejected() throws Exception {
        mockMvc.perform(as(momToken, post("/api/admin/admins")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "DAD@example.com", "password": "Passw0rd!", "name": "x"}
                        """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("EMAIL_ALREADY_EXISTS"));
        mockMvc.perform(as(momToken, post("/api/admin/admins")).contentType(MediaType.APPLICATION_JSON).content("""
                        {"email": "new@example.com", "password": "short", "name": "x"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("password"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("需為 8 到 72 個英文字母、數字或符號"));
    }

    // ===== 停用 =====

    @Test
    void disable_takesEffectImmediately_onExistingToken() throws Exception {
        String dadToken = jwtService.generateToken(dad);
        mockMvc.perform(as(dadToken, get("/api/users/me"))).andExpect(status().isOk());

        mockMvc.perform(as(momToken, patch("/api/admin/admins/" + dad.getId() + "/enabled"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.enabled").value(false));

        // 爸爸手上的 token 還沒過期，但帳號停用後立刻無法使用，也無法再登入
        mockMvc.perform(as(dadToken, get("/api/users/me"))).andExpect(status().isUnauthorized());
        login("dad@example.com", PASSWORD).andExpect(status().isUnauthorized());

        // 重新啟用後可以再登入
        mockMvc.perform(as(momToken, patch("/api/admin/admins/" + dad.getId() + "/enabled"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": true}"))
                .andExpect(status().isOk());
        login("dad@example.com", PASSWORD).andExpect(status().isOk());
    }

    @Test
    void cannotDisableYourself() throws Exception {
        mockMvc.perform(as(momToken, patch("/api/admin/admins/" + mom.getId() + "/enabled"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CANNOT_DISABLE_SELF"));

        assertThat(userRepository.findById(mom.getId()).orElseThrow().isEnabled()).isTrue();
    }

    @Test
    void customerAccounts_areNotManagedHere() throws Exception {
        User customer = user("customer@example.com", "顧客", Role.USER);

        mockMvc.perform(as(momToken, patch("/api/admin/admins/" + customer.getId() + "/enabled"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"enabled\": false}"))
                .andExpect(status().isNotFound());
        mockMvc.perform(as(momToken, put("/api/admin/admins/" + customer.getId() + "/password"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"NewPass123!\"}"))
                .andExpect(status().isNotFound());
    }

    // ===== 重設密碼 =====

    @Test
    void resetPassword_oldPasswordStopsWorking_newOneWorks() throws Exception {
        mockMvc.perform(as(momToken, put("/api/admin/admins/" + dad.getId() + "/password"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"NewPass123!\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.passwordChangedAt").exists());

        login("dad@example.com", PASSWORD).andExpect(status().isUnauthorized());
        login("dad@example.com", "NewPass123!").andExpect(status().isOk());
    }

    @Test
    void resetPassword_invalidatesTokensIssuedBefore() throws Exception {
        // 爸爸的 token 是 1 分鐘前發的（模擬「被盜用的舊登入」）
        String oldDadToken = tokenIssuedAt(dad, Instant.now().minusSeconds(60));
        mockMvc.perform(as(oldDadToken, get("/api/users/me"))).andExpect(status().isOk());

        mockMvc.perform(as(momToken, put("/api/admin/admins/" + dad.getId() + "/password"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"NewPass123!\"}"))
                .andExpect(status().isOk());

        // 舊 token 立刻失效；重新登入拿到的新 token 可以用
        mockMvc.perform(as(oldDadToken, get("/api/users/me"))).andExpect(status().isUnauthorized());
        String newToken = JsonPath.read(login("dad@example.com", "NewPass123!")
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8), "$.data.accessToken");
        mockMvc.perform(as(newToken, get("/api/users/me"))).andExpect(status().isOk());
    }

    @Test
    void otherAdminsTokens_areNotAffected() throws Exception {
        String oldMomToken = tokenIssuedAt(mom, Instant.now().minusSeconds(60));

        mockMvc.perform(as(momToken, put("/api/admin/admins/" + dad.getId() + "/password"))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"newPassword\": \"NewPass123!\"}"))
                .andExpect(status().isOk());

        // 改的是爸爸的密碼，媽媽的舊 token 不受影響
        mockMvc.perform(as(oldMomToken, get("/api/users/me"))).andExpect(status().isOk());
    }

    // ===== helpers =====

    private User user(String email, String name, Role role) {
        User u = new User();
        u.setEmail(email);
        u.setPasswordHash(passwordEncoder.encode(PASSWORD));
        u.setName(name);
        u.setRole(role);
        return userRepository.saveAndFlush(u);
    }

    private static MockHttpServletRequestBuilder as(String token, MockHttpServletRequestBuilder builder) {
        return builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }

    private ResultActions login(String email, String password) throws Exception {
        return mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\": \"%s\", \"password\": \"%s\"}".formatted(email, password)));
    }

    /** 產生指定發行時間的 token（模擬之前登入拿到的 token） */
    private String tokenIssuedAt(User user, Instant issuedAt) {
        return Jwts.builder()
                .subject(user.getId().toString())
                .issuedAt(Date.from(issuedAt))
                .expiration(Date.from(issuedAt.plusSeconds(3600)))
                .signWith(Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8)))
                .compact();
    }
}
