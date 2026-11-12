package com.mamashop.user.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.user.dto.LoginRequest;
import com.mamashop.user.dto.LoginResponse;
import com.mamashop.user.dto.RegisterRequest;
import com.mamashop.user.dto.UserResponse;
import com.mamashop.user.service.AuthService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "會員：註冊與登入", description = "註冊、登入（不需登入）")
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    /** 註冊顧客帳號。成功回 201，之後再呼叫 /login 取得 token。 */
    @Operation(summary = "註冊顧客帳號")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ApiResponse.ok(authService.register(request));
    }

    /** 登入，回傳 JWT。 */
    @Operation(summary = "登入，取得 JWT")
    @PostMapping("/login")
    public ApiResponse<LoginResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiResponse.ok(authService.login(request));
    }
}
