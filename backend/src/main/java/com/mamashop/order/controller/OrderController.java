package com.mamashop.order.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.common.response.PageResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.dto.OrderSummaryResponse;
import com.mamashop.order.service.OrderService;
import com.mamashop.security.AuthUser;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顧客訂單。需要登入（SecurityConfig：未列在公開清單的路徑都要登入）。
 * 會員 id 一律從 token 取得（@AuthenticationPrincipal），不接受前端傳入，所以只能操作自己的訂單。
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    /** 建立訂單（結帳）。成功回 201 與訂單明細。 */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<OrderDetailResponse> create(@AuthenticationPrincipal AuthUser authUser,
                                                   @Valid @RequestBody CreateOrderRequest request) {
        return ApiResponse.ok(orderService.createOrder(authUser.id(), request));
    }

    /** 我的訂單（新到舊） */
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(orderService.listMyOrders(authUser.id(), page, size));
    }

    /** 訂單詳情（只能看自己的；別人的回 404） */
    @GetMapping("/{orderNo}")
    public ApiResponse<OrderDetailResponse> detail(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(orderService.getMyOrder(authUser.id(), orderNo));
    }

    /**
     * 取消訂單（只有待確認時可以）。
     * 用 POST /{orderNo}/cancel 表達「動作」，而不是讓前端 PATCH 任意的 status，
     * 這樣顧客不可能把訂單改成「已完成」之類的狀態。
     */
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(orderService.cancelMyOrder(authUser.id(), orderNo));
    }
}
