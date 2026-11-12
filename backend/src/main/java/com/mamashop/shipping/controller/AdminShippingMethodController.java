package com.mamashop.shipping.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.shipping.dto.AdminShippingMethodResponse;
import com.mamashop.shipping.dto.ShippingMethodUpdateRequest;
import com.mamashop.shipping.service.ShippingMethodService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 後台運送方式與運費設定 */
@RestController
@RequestMapping("/api/admin/shipping-methods")
public class AdminShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    public AdminShippingMethodController(ShippingMethodService shippingMethodService) {
        this.shippingMethodService = shippingMethodService;
    }

    /** 全部運送方式（含停用） */
    @GetMapping
    public ApiResponse<List<AdminShippingMethodResponse>> list() {
        return ApiResponse.ok(shippingMethodService.listForAdmin());
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminShippingMethodResponse> update(@PathVariable @Positive Long id,
                                                           @Valid @RequestBody ShippingMethodUpdateRequest request) {
        return ApiResponse.ok(shippingMethodService.update(id, request));
    }
}
