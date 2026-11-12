package com.mamashop.shipping.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.shipping.dto.AdminShippingMethodResponse;
import com.mamashop.shipping.dto.ShippingMethodUpdateRequest;
import com.mamashop.shipping.service.ShippingMethodService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@Tag(name = "後台：運送方式", description = "限管理員")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/shipping-methods")
public class AdminShippingMethodController {

    private final ShippingMethodService shippingMethodService;

    public AdminShippingMethodController(ShippingMethodService shippingMethodService) {
        this.shippingMethodService = shippingMethodService;
    }

    /** 全部運送方式（含停用） */
    @Operation(summary = "運送方式列表（含停用）")
    @GetMapping
    public ApiResponse<List<AdminShippingMethodResponse>> list() {
        return ApiResponse.ok(shippingMethodService.listForAdmin());
    }

    @Operation(summary = "修改名稱、運費、啟用狀態")
    @PutMapping("/{id}")
    public ApiResponse<AdminShippingMethodResponse> update(@PathVariable @Positive Long id,
                                                           @Valid @RequestBody ShippingMethodUpdateRequest request) {
        return ApiResponse.ok(shippingMethodService.update(id, request));
    }
}
