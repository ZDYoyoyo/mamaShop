package com.mamashop.security;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpHeaders;
import org.springframework.test.json.JsonCompareMode;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * 健康檢查（D07）：給 Docker 與外部監控服務用。
 *
 * <p>AccessControlTest 的完整性檢查只掃描 Controller 的 API，看不到 Actuator 的端點，
 * 所以 Actuator 的權限在這裡另外測。
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class HealthEndpointTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private UserRepository userRepository;

    @Test
    void health_isPublic_andShowsOnlyStatus() throws Exception {
        // 不用登入；只有 UP／DOWN，沒有資料庫種類、硬碟空間等細節
        mockMvc.perform(get("/api/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(content().json("{\"status\":\"UP\"}", JsonCompareMode.STRICT));
    }

    /** 其他 Actuator 端點會洩漏設定、環境變數、記憶體內容：沒登入的人連看都不行 */
    @ParameterizedTest
    @ValueSource(strings = {"/api/actuator", "/api/actuator/env", "/api/actuator/beans", "/api/actuator/heapdump",
            "/api/actuator/health/db"})
    void otherEndpoints_requireLogin(String path) throws Exception {
        mockMvc.perform(get(path)).andExpect(status().isUnauthorized());
    }

    /** 就算是管理員也拿不到：這些端點根本沒有暴露（application.yml 只開放 health） */
    @ParameterizedTest
    @ValueSource(strings = {"/api/actuator/env", "/api/actuator/beans", "/api/actuator/heapdump",
            "/api/actuator/configprops"})
    void otherEndpoints_areNotExposed_evenForAdmin(String path) throws Exception {
        User admin = new User();
        admin.setEmail("health-admin@example.com");
        admin.setPasswordHash("not-used");
        admin.setName("健康檢查測試");
        admin.setRole(Role.ADMIN);
        String token = jwtService.generateToken(userRepository.save(admin));

        mockMvc.perform(get(path).header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(status().isNotFound());
    }
}
