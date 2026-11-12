package com.mamashop.user.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.security.AuthUser;
import com.mamashop.user.dto.AccountEnabledRequest;
import com.mamashop.user.dto.AdminAccountResponse;
import com.mamashop.user.dto.CreateAdminRequest;
import com.mamashop.user.dto.ResetPasswordRequest;
import com.mamashop.user.service.AdminAccountService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 後台管理員帳號管理（/api/admin/** 限 ADMIN）。不提供刪除，不用的帳號停用即可。 */
@Tag(name = "後台：管理員帳號", description = "限管理員。停用、重設密碼立即生效")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/admin/admins")
public class AdminAccountController {

    private final AdminAccountService adminAccountService;

    public AdminAccountController(AdminAccountService adminAccountService) {
        this.adminAccountService = adminAccountService;
    }

    @Operation(summary = "管理員列表")
    @GetMapping
    public ApiResponse<List<AdminAccountResponse>> list() {
        return ApiResponse.ok(adminAccountService.list());
    }

    @Operation(summary = "新增管理員")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminAccountResponse> create(@Valid @RequestBody CreateAdminRequest request) {
        return ApiResponse.ok(adminAccountService.create(request));
    }

    /** 停用／啟用：{"enabled": false}。不能停用自己 */
    @Operation(summary = "停用／啟用管理員（不能停用自己）")
    @PatchMapping("/{id}/enabled")
    public ApiResponse<AdminAccountResponse> setEnabled(@AuthenticationPrincipal AuthUser operator,
                                                        @PathVariable @Positive Long id,
                                                        @Valid @RequestBody AccountEnabledRequest request) {
        return ApiResponse.ok(adminAccountService.setEnabled(operator.id(), id, request.enabled()));
    }

    /** 重設密碼：{"newPassword": "..."}。該帳號之前的登入全部失效 */
    @Operation(summary = "重設密碼（該帳號之前的登入全部失效）")
    @PutMapping("/{id}/password")
    public ApiResponse<AdminAccountResponse> resetPassword(@PathVariable @Positive Long id,
                                                           @Valid @RequestBody ResetPasswordRequest request) {
        return ApiResponse.ok(adminAccountService.resetPassword(id, request.newPassword()));
    }
}
