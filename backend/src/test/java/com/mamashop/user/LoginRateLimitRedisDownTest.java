package com.mamashop.user;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Redis 連不到時（port 1 沒有東西在聽）：登入照常運作，只是不限制次數。
 * Redis 壞掉不能讓所有人都登不進去。
 */
@SpringBootTest(properties = {
        "app.login-limit.enabled=true",
        "app.login-limit.max-failures-per-account=2",
        "spring.data.redis.port=1"})
@AutoConfigureMockMvc
class LoginRateLimitRedisDownTest {

    @Autowired private MockMvc mockMvc;

    @Test
    void loginStillWorks_withoutLimit() throws Exception {
        // 超過上限的次數，每次都是一般的「帳號或密碼錯誤」（401），不是 500、也不是被鎖（429）
        for (int i = 0; i < 4; i++) {
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content("{\"email\":\"nobody@example.com\",\"password\":\"wrong-password\"}"))
                    .andExpect(status().isUnauthorized());
        }
    }
}
