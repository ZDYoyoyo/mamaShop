package com.mamashop.order.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.order.dto.CreateOrderRequest;
import com.mamashop.order.dto.OrderDetailResponse;
import com.mamashop.order.service.OrderService;
import com.mamashop.security.AuthUser;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 顧客訂單。需要登入（SecurityConfig：未列在公開清單的路徑都要登入）。 */
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
}
