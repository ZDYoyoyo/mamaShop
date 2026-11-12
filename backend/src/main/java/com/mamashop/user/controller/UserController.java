package com.mamashop.user.controller;

import com.mamashop.common.config.OpenApiConfig;
import com.mamashop.common.response.ApiResponse;
import com.mamashop.security.AuthUser;
import com.mamashop.user.dto.UserResponse;
import com.mamashop.user.service.UserService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "會員", description = "目前登入者的資料")
@SecurityRequirement(name = OpenApiConfig.BEARER_AUTH)
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /**
     * 目前登入者的資料（需要登入）。前端重新整理頁面時用來確認 token 還有效。
     * @AuthenticationPrincipal 會注入 JwtAuthenticationFilter 放進 SecurityContext 的 AuthUser；
     * 會員 id 一律從 token 取得，不接受前端傳入，避免查到別人的資料。
     */
    @Operation(summary = "目前登入者的資料")
    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal AuthUser authUser) {
        return ApiResponse.ok(userService.getProfile(authUser.id()));
    }
}
