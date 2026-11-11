package com.mamashop.common.exception;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mamashop.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用一個測試專用的 Controller 觸發各種錯誤，確認回應都是統一格式。
 * @WebMvcTest 只載入 Web 層（Controller、ControllerAdvice），不連資料庫，跑得很快。
 */
@WebMvcTest(GlobalExceptionHandlerTest.TestController.class)
// 這裡只測 Controller 邏輯，關掉 Security filter；權限規則由 SecurityIntegrationTest 驗證
@AutoConfigureMockMvc(addFilters = false)
@Import(GlobalExceptionHandlerTest.TestController.class)
class GlobalExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void success_wrapsData() throws Exception {
        mockMvc.perform(get("/__test/ok"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.name").value("帆布托特包"))
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void invalidRequestBody_returns400WithFieldErrors() throws Exception {
        mockMvc.perform(post("/__test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        // 用英文語系送出，確認訊息仍是繁體中文
                        .header("Accept-Language", "en-US")
                        .content("""
                                {"name": " ", "email": "not-an-email", "quantity": 0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors", hasSize(3)))
                .andExpect(jsonPath("$.error.fieldErrors[*].field",
                        containsInAnyOrder("name", "email", "quantity")))
                .andExpect(jsonPath("$.error.fieldErrors[*].message",
                        containsInAnyOrder("為必填", "Email 格式不正確", "不可小於 1")));
    }

    @Test
    void invalidRequestParam_returns400WithParamName() throws Exception {
        mockMvc.perform(get("/__test/param").param("page", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("page"))
                .andExpect(jsonPath("$.error.fieldErrors[0].message").value("不可小於 1"));
    }

    @Test
    void missingRequestParam_returns400() throws Exception {
        mockMvc.perform(get("/__test/param"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("page"));
    }

    @Test
    void pathVariableTypeMismatch_returns400() throws Exception {
        mockMvc.perform(get("/__test/items/abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("id"));
    }

    @Test
    void malformedJson_returns400() throws Exception {
        mockMvc.perform(post("/__test/body")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{ this is not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("BAD_REQUEST"));
    }

    @Test
    void unsupportedContentType_keeps415() throws Exception {
        mockMvc.perform(post("/__test/body")
                        .contentType(MediaType.TEXT_PLAIN)
                        .content("hello"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void notFoundException_returns404WithMessage() throws Exception {
        mockMvc.perform(get("/__test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.error.message").value("找不到商品"))
                .andExpect(jsonPath("$.error.fieldErrors").doesNotExist());
    }

    @Test
    void unknownPath_returns404() throws Exception {
        mockMvc.perform(get("/__test/no-such-path"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("NOT_FOUND"));
    }

    @Test
    void wrongHttpMethod_returns405() throws Exception {
        mockMvc.perform(post("/__test/ok"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.error.code").value("METHOD_NOT_ALLOWED"));
    }

    @Test
    void optimisticLock_returns409() throws Exception {
        mockMvc.perform(get("/__test/lock"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("CONFLICT"));
    }

    @Test
    void unexpectedException_returns500_withoutLeakingDetails() throws Exception {
        mockMvc.perform(get("/__test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error.code").value("INTERNAL_ERROR"))
                .andExpect(jsonPath("$.error.message").value("系統發生錯誤，請稍後再試"))
                .andExpect(content().string(not(containsString("db-password"))));
    }

    // ===== 測試專用 =====

    record SampleRequest(@NotBlank String name, @Email String email, @Min(1) int quantity) {
    }

    @RestController
    @RequestMapping("/__test")
    static class TestController {

        @GetMapping("/ok")
        ApiResponse<Map<String, String>> ok() {
            return ApiResponse.ok(Map.of("name", "帆布托特包"));
        }

        @PostMapping("/body")
        ApiResponse<Void> body(@Valid @RequestBody SampleRequest request) {
            return ApiResponse.ok();
        }

        @GetMapping("/param")
        ApiResponse<Void> param(@RequestParam @Min(1) int page) {
            return ApiResponse.ok();
        }

        @GetMapping("/items/{id}")
        ApiResponse<Void> item(@PathVariable Long id) {
            return ApiResponse.ok();
        }

        @GetMapping("/not-found")
        ApiResponse<Void> notFound() {
            throw new NotFoundException("找不到商品");
        }

        @GetMapping("/lock")
        ApiResponse<Void> lock() {
            throw new ObjectOptimisticLockingFailureException(Object.class, 1L);
        }

        @GetMapping("/boom")
        ApiResponse<Void> boom() {
            throw new IllegalStateException("connection failed: db-password=secret");
        }
    }
}
