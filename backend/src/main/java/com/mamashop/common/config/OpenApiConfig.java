package com.mamashop.common.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * API 文件（springdoc-openapi）的全域設定。
 *
 * <p>springdoc 啟動時會掃描所有 Controller，自動產生 OpenAPI 規格（/v3/api-docs）與 Swagger UI。
 * 參數、型別、驗證規則（@NotBlank、@Size…）都會自動出現在文件上，不用另外維護。
 *
 * <p>登入：Swagger UI 右上角「Authorize」貼上 /api/auth/login 回傳的 accessToken（不用加 Bearer），
 * 之後標有鎖頭的 API 都會自動帶上 Authorization header。
 */
@Configuration
@OpenAPIDefinition(info = @Info(
        title = "mamaShop API",
        version = "v1",
        description = """
                家人經營的電商網站後端 API。

                - 統一回應格式：`{ "success": true, "data": ... }` 或 `{ "success": false, "error": { "code", "message", "fieldErrors" } }`
                - 需要登入的 API：先呼叫「會員」的登入取得 token，再按右上角 Authorize
                - 後台 API（/api/admin/**）只有管理員可以使用
                """))
@SecurityScheme(
        name = OpenApiConfig.BEARER_AUTH,
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT")
public class OpenApiConfig {

    /** 在需要登入的 Controller 上標註 @SecurityRequirement(name = OpenApiConfig.BEARER_AUTH) */
    public static final String BEARER_AUTH = "bearerAuth";
}
