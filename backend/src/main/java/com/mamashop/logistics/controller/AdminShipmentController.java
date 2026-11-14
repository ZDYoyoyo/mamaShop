package com.mamashop.logistics.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.logistics.dto.LabelFormResponse;
import com.mamashop.logistics.dto.ShipmentResponse;
import com.mamashop.logistics.service.ShipmentService;
import com.mamashop.order.dto.AdminOrderActionRequest;
import com.mamashop.order.dto.AdminOrderDetailResponse;
import com.mamashop.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 後台：綠界物流單（P03）。/api/admin/** 只有管理員能用（SecurityConfig） */
@Tag(name = "後台物流單", description = "超商取貨訂單建立綠界物流單、列印寄件單")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@Validated
@RestController
@RequestMapping("/api/admin/orders/{orderNo}/shipment")
public class AdminShipmentController {

    private final ShipmentService shipmentService;

    public AdminShipmentController(ShipmentService shipmentService) {
        this.shipmentService = shipmentService;
    }

    /** 這張訂單的物流單；還沒建立時 data 是 null */
    @Operation(summary = "物流單（含最新物流狀態）")
    @GetMapping
    public ApiResponse<ShipmentResponse> get(@PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(shipmentService.findShipment(orderNo).orElse(null));
    }

    /** 建立物流單並出貨：{"version": 1, "note": "..."}，回傳更新後的訂單（已出貨） */
    @Operation(summary = "建立綠界物流單並出貨（備貨中的超商取貨訂單）")
    @PostMapping
    public ApiResponse<AdminOrderDetailResponse> create(@AuthenticationPrincipal AuthUser admin,
                                                        @PathVariable @Size(max = 32) String orderNo,
                                                        @Valid @RequestBody AdminOrderActionRequest request) {
        return ApiResponse.ok(shipmentService.createShipment(orderNo, request.version(), admin.id(), request.note()));
    }

    /** 列印寄件單：回傳要 POST 到綠界的表單 */
    @Operation(summary = "列印寄件單（回傳綠界的表單）")
    @GetMapping("/label")
    public ApiResponse<LabelFormResponse> label(@PathVariable @Size(max = 32) String orderNo) {
        return ApiResponse.ok(shipmentService.labelForm(orderNo));
    }
}
