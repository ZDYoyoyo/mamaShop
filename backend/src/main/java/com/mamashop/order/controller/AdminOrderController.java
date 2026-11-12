package com.mamashop.order.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.common.response.PageResponse;
import com.mamashop.order.dto.AdminOrderActionRequest;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.order.dto.AdminOrderSummaryResponse;
import com.mamashop.order.dto.ReturnOrderRequest;
import com.mamashop.order.dto.ShipOrderRequest;
import com.mamashop.order.entity.OrderStatus;
import com.mamashop.order.service.AdminOrderService;
import com.mamashop.security.AuthUser;
import com.mamashop.shipping.entity.ShippingMethodCode;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 後台訂單管理（/api/admin/** 限 ADMIN）。
 *
 * <p>每個操作是一支獨立的 API（POST /confirm、/ship…），而不是一支「PATCH 任意 status」：
 * 每個動作需要的資料不同（出貨要物流單號、退回要決定是否補庫存），附帶的效果也不同（完成要標記已付款、取消要歸還庫存）。
 */
@RestController
@RequestMapping("/api/admin/orders")
public class AdminOrderController {

    private final AdminOrderService adminOrderService;

    public AdminOrderController(AdminOrderService adminOrderService) {
        this.adminOrderService = adminOrderService;
    }

    /** 訂單列表。例如待確認的訂單：GET /api/admin/orders?status=PENDING_CONFIRM */
    @GetMapping
    public ApiResponse<PageResponse<AdminOrderSummaryResponse>> list(
            @RequestParam(required = false) OrderStatus status,
            @RequestParam(required = false) ShippingMethodCode shippingMethod,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(adminOrderService.search(status, shippingMethod, page, size));
    }

    /** 訂單詳情：含下單會員、狀態紀錄、版本號 */
    @GetMapping("/{orderNo}")
    public ApiResponse<AdminOrderDetailResponse> get(@PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(adminOrderService.get(orderNo));
    }

    /** 確認訂單：{"version": 0, "note": "已電話確認"} */
    @PostMapping("/{orderNo}/confirm")
    public ApiResponse<AdminOrderDetailResponse> confirm(@AuthenticationPrincipal AuthUser admin,
                                                         @PathVariable @Size(max = 32) String orderNo,
                                                         @Valid @RequestBody AdminOrderActionRequest request) {
        return ApiResponse.ok(adminOrderService.confirm(orderNo, request.version(), admin.id(), request.note()));
    }

    /** 出貨：{"version": 1, "trackingNo": "123456789"} */
    @PostMapping("/{orderNo}/ship")
    public ApiResponse<AdminOrderDetailResponse> ship(@AuthenticationPrincipal AuthUser admin,
                                                      @PathVariable @Size(max = 32) String orderNo,
                                                      @Valid @RequestBody ShipOrderRequest request) {
        return ApiResponse.ok(adminOrderService.ship(
                orderNo, request.version(), request.trackingNo(), admin.id(), request.note()));
    }

    /** 已送達／已到店 */
    @PostMapping("/{orderNo}/deliver")
    public ApiResponse<AdminOrderDetailResponse> deliver(@AuthenticationPrincipal AuthUser admin,
                                                         @PathVariable @Size(max = 32) String orderNo,
                                                         @Valid @RequestBody AdminOrderActionRequest request) {
        return ApiResponse.ok(adminOrderService.deliver(orderNo, request.version(), admin.id(), request.note()));
    }

    /** 完成（同時標記已收款） */
    @PostMapping("/{orderNo}/complete")
    public ApiResponse<AdminOrderDetailResponse> complete(@AuthenticationPrincipal AuthUser admin,
                                                          @PathVariable @Size(max = 32) String orderNo,
                                                          @Valid @RequestBody AdminOrderActionRequest request) {
        return ApiResponse.ok(adminOrderService.complete(orderNo, request.version(), admin.id(), request.note()));
    }

    /** 取消（出貨前），歸還庫存 */
    @PostMapping("/{orderNo}/cancel")
    public ApiResponse<AdminOrderDetailResponse> cancel(@AuthenticationPrincipal AuthUser admin,
                                                        @PathVariable @Size(max = 32) String orderNo,
                                                        @Valid @RequestBody AdminOrderActionRequest request) {
        return ApiResponse.ok(adminOrderService.cancel(orderNo, request.version(), admin.id(), request.note()));
    }

    /** 退回：{"version": 3, "restock": true, "note": "超商未取件"} */
    @PostMapping("/{orderNo}/return")
    public ApiResponse<AdminOrderDetailResponse> markReturned(@AuthenticationPrincipal AuthUser admin,
                                                              @PathVariable @Size(max = 32) String orderNo,
                                                              @Valid @RequestBody ReturnOrderRequest request) {
        return ApiResponse.ok(adminOrderService.markReturned(
                orderNo, request.version(), request.restock(), admin.id(), request.note()));
    }
}
