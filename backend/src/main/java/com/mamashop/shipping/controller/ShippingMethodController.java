package com.mamashop.shipping.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.shipping.dto.ShippingMethodResponse;
import com.mamashop.shipping.service.ShippingMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "前台：商品")
@RestController
@RequestMapping("/api/shipping-methods")
public class ShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    public ShippingMethodController(ShippingMethodService shippingMethodService) {
        this.shippingMethodService = shippingMethodService;
    }

    /** 可用的運送方式與運費（結帳頁用） */
    @Operation(summary = "可用的運送方式與運費")
    @GetMapping
    public ApiResponse<List<ShippingMethodResponse>> list() {
        return ApiResponse.ok(shippingMethodService.listEnabledMethods());
    }
}
