package com.mamashop.dashboard.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.dashboard.dto.AdminDashboardResponse;
import com.mamashop.dashboard.service.AdminDashboardService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 後台首頁概覽。路徑在 /api/admin/** 底下，SecurityConfig 已限定 ADMIN */
@Tag(name = "後台：概覽", description = "限管理員")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/dashboard")
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    public AdminDashboardController(AdminDashboardService adminDashboardService) {
        this.adminDashboardService = adminDashboardService;
    }

    @Operation(summary = "首頁概覽：今日訂單、待確認、待出貨、未取件、最近退回、低庫存商品")
    @GetMapping
    public ApiResponse<AdminDashboardResponse> get() {
        return ApiResponse.ok(adminDashboardService.get());
    }
}
