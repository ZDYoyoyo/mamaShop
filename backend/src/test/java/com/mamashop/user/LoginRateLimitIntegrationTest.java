package com.mamashop.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

/**
 * 登入失敗次數限制：真的 Redis，從 API 打進去（MockMvc）。
 * 每個測試用自己的 Email 與 IP（MockMvc 可以指定來源 IP），互不影響；上限調小（帳號 3 次、IP 6 次）方便測試。
 */
@SpringBootTest(properties = {
        "app.login-limit.enabled=true",
        "app.login-limit.max-failures-per-account=3",
        "app.login-limit.max-failures-per-ip=6",
        "app.login-limit.window=1m"})
@AutoConfigureMockMvc
class LoginRateLimitIntegrationTest {

    private static final String PASSWORD = "Passw0rd!x";

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private StringRedisTemplate redis;

    private String email;
    private String ip;
    private String otherIp;
    private User user;

    @BeforeEach
    void setUp() {
        String suffix = UUID.randomUUID().toString().substring(0, 8);
        email = "limit-" + suffix + "@example.com";
        // 每個測試一組沒用過的 IP（198.18.0.0/15 是保留給測試用的位址）
        ip = "198.18." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        otherIp = "198.19." + (int) (Math.random() * 250) + "." + (int) (Math.random() * 250);
        user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        user.setName("限制測試");
        user.setRole(Role.USER);
        user = userRepository.save(user);
    }

    @AfterEach
    void cleanUp() {
        userRepository.delete(user);
        redis.delete(redis.keys("mamashop:login-fail:*" + ip + "*"));
        redis.delete(redis.keys("mamashop:login-fail:*" + otherIp + "*"));
    }

    @Test
    void afterTooManyFailures_evenTheCorrectPasswordIsRejected() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        // 第 4 次：被鎖住。密碼是對的也不行，而且訊息不透露密碼對不對
        login(email, PASSWORD, ip)
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.error.code").value("TOO_MANY_LOGIN_ATTEMPTS"))
                .andExpect(jsonPath("$.error.message").value("登入失敗次數過多，請 1 分鐘後再試"));
    }

    @Test
    void lockIsPerSource_ownerCanStillLoginFromAnotherIp() throws Exception {
        // 有人在別的地方亂猜，把「那個來源」鎖住了
        for (int i = 0; i < 3; i++) {
            login(email, "wrong-password", ip);
        }
        login(email, PASSWORD, ip).andExpect(status().isTooManyRequests());

        // 本人從自己的網路照常登入：不會被別人鎖在外面
        login(email, PASSWORD, otherIp).andExpect(status().isOk());
    }

    @Test
    void successResetsTheAccountCounter() throws Exception {
        login(email, "wrong-password", ip);
        login(email, "wrong-password", ip);
        login(email, PASSWORD, ip).andExpect(status().isOk());

        // 成功之後重新計算：再錯 2 次還不會被鎖
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, "wrong-password", ip).andExpect(status().isUnauthorized());
        login(email, PASSWORD, ip).andExpect(status().isOk());
    }

    @Test
    void sameIpGuessingManyAccounts_isBlockedByIpLimit() throws Exception {
        // 同一個來源換著不同帳號猜（包含不存在的帳號）：每個帳號都沒超過 3 次，但這個 IP 總共 6 次
        for (int i = 0; i < 6; i++) {
            login("nobody-" + i + "-" + email, "wrong-password", ip).andExpect(status().isUnauthorized());
        }

        // 這個 IP 被鎖：連正確的帳號密碼也不行
        login(email, PASSWORD, ip).andExpect(status().isTooManyRequests());
        // 別的 IP 不受影響
        login(email, PASSWORD, otherIp).andExpect(status().isOk());
    }

    @Test
    void countersExpire() throws Exception {
        login(email, "wrong-password", ip);

        // 計數有設定到期時間（不會永遠鎖住）：1 分鐘內的某個正數
        String key = redis.keys("mamashop:login-fail:ip:" + ip).iterator().next();
        assertThat(redis.getExpire(key)).isBetween(1L, 60L);
    }

    private ResultActions login(String loginEmail, String password, String fromIp) throws Exception {
        return mockMvc.perform(post("/api/auth/login")
                .with(request -> {
                    request.setRemoteAddr(fromIp);
                    return request;
                })
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"email\":\"" + loginEmail + "\",\"password\":\"" + password + "\"}"));
    }
}
