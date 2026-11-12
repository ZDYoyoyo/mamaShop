package com.mamashop.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

/**
 * API 文件（B13）。用 properties 明確指定開關，不依賴 .env 的 API_DOCS_ENABLED。
 */
class ApiDocsTest {

    @Nested
    @SpringBootTest(properties = {"springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
    @AutoConfigureMockMvc
    class WhenEnabled {

        @Autowired private MockMvc mockMvc;

        @Test
        void openApiSpec_listsAllApis_withJwtSecurity() throws Exception {
            String body = mockMvc.perform(get("/v3/api-docs"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.info.title").value("mamaShop API"))
                    .andExpect(jsonPath("$.components.securitySchemes.bearerAuth.scheme").value("bearer"))
                    .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);

            Map<String, Object> paths = JsonPath.read(body, "$.paths");
            assertThat(paths).containsKeys("/api/products", "/api/orders", "/api/admin/orders/{orderNo}/ship");

            // 需要登入的 API 標有 bearerAuth（Swagger UI 顯示鎖頭、自動帶 token）；公開 API 沒有
            List<?> orderSecurity = JsonPath.read(body, "$.paths['/api/orders'].get.security");
            assertThat(orderSecurity).isNotEmpty();
            Map<String, Object> productListOperation = JsonPath.read(body, "$.paths['/api/products'].get");
            assertThat(productListOperation).doesNotContainKey("security");

            // @AuthenticationPrincipal 的參數不該出現在文件上（它來自 token，不是讓人填的）
            List<String> params = JsonPath.read(body, "$.paths['/api/orders'].get.parameters[*].name");
            assertThat(params).containsExactlyInAnyOrder("page", "size");
        }

        @Test
        void swaggerUi_isReachableWithoutLogin() throws Exception {
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
        }
    }

    @Nested
    @SpringBootTest(properties = {"springdoc.api-docs.enabled=false", "springdoc.swagger-ui.enabled=false"})
    @AutoConfigureMockMvc
    class WhenDisabled {

        @Autowired private MockMvc mockMvc;

        @Test
        void docsAreNotExposed() throws Exception {
            mockMvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
            mockMvc.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
        }
    }
}
