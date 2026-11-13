package com.mamashop.order.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mamashop.order.service.AdminOrderService;
import com.mamashop.order.service.CheckoutTokenService;
import com.mamashop.order.service.OrderService;
import com.mamashop.order.service.OrderSubmissionService;
import com.mamashop.security.AuthUser;
import com.mamashop.user.entity.Role;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 訂單 Controller 的參數測試：只測 Web 層（參數驗證、預設值、從 token 取會員 id），
 * Service 用 Mockito 假造，Security filter 關閉（權限在 AccessControlTest 測）。
 */
@WebMvcTest({OrderController.class, AdminOrderController.class})
@AutoConfigureMockMvc(addFilters = false)
class OrderControllerParamTest {

    private static final AuthUser CUSTOMER = new AuthUser(42L, "c@example.com", Role.USER);
    private static final AuthUser ADMIN = new AuthUser(7L, "a@example.com", Role.ADMIN);

    @Autowired private MockMvc mockMvc;
    @MockitoBean private OrderService orderService;
    @MockitoBean private AdminOrderService adminOrderService;
    @MockitoBean private OrderSubmissionService orderSubmissionService;
    @MockitoBean private CheckoutTokenService checkoutTokenService;

    // ===== 顧客 =====

    @Test
    void myOrders_defaultPaging_usesUserIdFromToken() throws Exception {
        mockMvc.perform(as(CUSTOMER, get("/api/orders"))).andExpect(status().isOk());

        verify(orderService).listMyOrders(42L, 1, 10);
    }

    @Test
    void myOrders_invalidPaging_returns400() throws Exception {
        mockMvc.perform(as(CUSTOMER, get("/api/orders").param("page", "0").param("size", "51")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2));
        verify(orderService, never()).listMyOrders(anyLong(), anyInt(), anyInt());
    }

    @Test
    void orderNoTooLong_returns400() throws Exception {
        mockMvc.perform(as(CUSTOMER, get("/api/orders/" + "M".repeat(33))))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancel_usesUserIdFromToken() throws Exception {
        mockMvc.perform(as(CUSTOMER, post("/api/orders/M123/cancel"))).andExpect(status().isOk());

        verify(orderService).cancelMyOrder(42L, "M123");
    }

    @Test
    void createOrder_withInvalidBody_neverReachesService() throws Exception {
        mockMvc.perform(as(CUSTOMER, post("/api/orders").contentType(MediaType.APPLICATION_JSON).content("{}")))
                .andExpect(status().isBadRequest());
        verify(orderService, never()).createOrder(anyLong(), any());
    }

    // ===== 後台 =====

    @Test
    void adminList_invalidStatus_returns400() throws Exception {
        mockMvc.perform(as(ADMIN, get("/api/admin/orders").param("status", "NOT_A_STATUS")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("status"));
    }

    @Test
    void adminList_sizeOver100_returns400() throws Exception {
        mockMvc.perform(as(ADMIN, get("/api/admin/orders").param("size", "101")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminAction_requiresVersion() throws Exception {
        mockMvc.perform(as(ADMIN, post("/api/admin/orders/M123/confirm")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"note\": \"ok\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("version"));
        verify(adminOrderService, never()).confirm(anyString(), anyLong(), anyLong(), any());
    }

    @Test
    void adminAction_noteTooLong_returns400() throws Exception {
        mockMvc.perform(as(ADMIN, post("/api/admin/orders/M123/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"version\": 0, \"note\": \"" + "字".repeat(256) + "\"}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("note"));
    }

    @Test
    void adminShip_usesAdminIdFromToken() throws Exception {
        mockMvc.perform(as(ADMIN, post("/api/admin/orders/M123/ship")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 1, \"trackingNo\": \"T1\"}")))
                .andExpect(status().isOk());

        verify(adminOrderService).ship("M123", 1L, "T1", 7L, null);
    }

    @Test
    void adminReturn_requiresRestockDecision() throws Exception {
        mockMvc.perform(as(ADMIN, post("/api/admin/orders/M123/return")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"version\": 2}")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("restock"));
    }

    // ===== helpers =====

    /**
     * 模擬 JwtAuthenticationFilter 放進 SecurityContext 的登入者。
     * 這個測試關掉了 Security filter，所以直接設定 SecurityContextHolder
     * （MockMvc 和測試在同一個執行緒，Controller 的 @AuthenticationPrincipal 讀得到）。
     */
    private static MockHttpServletRequestBuilder as(AuthUser user, MockHttpServletRequestBuilder builder) {
        SecurityContextHolder.getContext().setAuthentication(
                UsernamePasswordAuthenticationToken.authenticated(user, null, user.authorities()));
        return builder;
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }
}
