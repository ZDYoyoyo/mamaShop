package com.mamashop.order.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.common.response.PageResponse;
import com.mamashop.order.dto.CheckoutTokenResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.dto.OrderSummaryResponse;
import com.mamashop.order.service.CheckoutTokenService;
import com.mamashop.order.service.OrderService;
import com.mamashop.order.service.OrderSubmissionService;
import com.mamashop.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 顧客訂單。需要登入（SecurityConfig：未列在公開清單的路徑都要登入）。
 * 會員 id 一律從 token 取得（@AuthenticationPrincipal），不接受前端傳入，所以只能操作自己的訂單。
 */
@Tag(name = "顧客訂單", description = "需要登入；只能看到、取消自己的訂單")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    /** 防重複下單的 header 名稱（業界常用的名稱，例如 Stripe 的 API） */
    public static final String IDEMPOTENCY_KEY = "Idempotency-Key";

    private final OrderService orderService;
    private final OrderSubmissionService orderSubmissionService;
    private final CheckoutTokenService checkoutTokenService;

    public OrderController(OrderService orderService, OrderSubmissionService orderSubmissionService,
                           CheckoutTokenService checkoutTokenService) {
        this.orderService = orderService;
        this.orderSubmissionService = orderSubmissionService;
        this.checkoutTokenService = checkoutTokenService;
    }

    /** 進入結帳頁時取得一次性識別碼（R02），送出訂單時放在 Idempotency-Key header */
    @Operation(summary = "取得結帳用的一次性識別碼（防重複下單）")
    @PostMapping("/checkout-token")
    public ApiResponse<CheckoutTokenResponse> checkoutToken(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(new CheckoutTokenResponse(
                checkoutTokenService.issue(authUser.id()), CheckoutTokenService.TTL.toSeconds()));
    }

    /**
     * 建立訂單（結帳）。header 要帶 Idempotency-Key（上面取得的識別碼）。
     * 新建立回 201；同一個識別碼已經下單成功過（網路重送、按兩次）回 200 和原本那筆訂單，不會重複建立。
     */
    @Operation(summary = "建立訂單（結帳）；同一個 Idempotency-Key 只會建立一筆")
    @PostMapping
    public ResponseEntity<ApiResponse<OrderDetailResponse>> create(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestHeader(name = IDEMPOTENCY_KEY, required = false) String idempotencyKey,
            @Valid @RequestBody CreateOrderRequest request) {
        OrderSubmissionService.Result result = orderSubmissionService.submit(authUser.id(), idempotencyKey, request);
        return ResponseEntity.status(result.replayed() ? HttpStatus.OK : HttpStatus.CREATED)
                .body(ApiResponse.ok(result.order()));
    }

    /** 我的訂單（新到舊） */
    @Operation(summary = "我的訂單")
    @GetMapping
    public ApiResponse<PageResponse<OrderSummaryResponse>> list(
            @AuthenticationPrincipal AuthUser authUser,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "10") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(orderService.listMyOrders(authUser.id(), page, size));
    }

    /** 訂單詳情（只能看自己的；別人的回 404） */
    @Operation(summary = "訂單詳情")
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
    @Operation(summary = "取消訂單（只有待確認時可以，歸還庫存）")
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<OrderDetailResponse> cancel(@AuthenticationPrincipal AuthUser authUser,
                                                   @PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(orderService.cancelMyOrder(authUser.id(), orderNo));
    }
}
