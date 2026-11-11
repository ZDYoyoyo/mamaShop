package com.mamashop.security;

import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.response.ApiError;
import com.mamashop.common.response.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Security 擋下的請求根本不會進到 Controller，所以 GlobalExceptionHandler 接不到，
 * 要在這裡自己寫出和其他 API 一樣格式的 JSON。
 * <ul>
 *   <li>401：沒登入、token 無效或過期（AuthenticationEntryPoint）</li>
 *   <li>403：已登入但權限不足，例如顧客呼叫 /api/admin/**（AccessDeniedHandler）</li>
 * </ul>
 */
@Component
public class SecurityErrorWriter implements AuthenticationEntryPoint, AccessDeniedHandler {

    private final JsonMapper jsonMapper;

    public SecurityErrorWriter(JsonMapper jsonMapper) {
        this.jsonMapper = jsonMapper;
    }

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
                         AuthenticationException ex) throws IOException {
        write(response, ErrorCode.UNAUTHORIZED);
    }

    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                       AccessDeniedException ex) throws IOException {
        write(response, ErrorCode.FORBIDDEN);
    }

    private void write(HttpServletResponse response, ErrorCode code) throws IOException {
        response.setStatus(code.getStatus().value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        jsonMapper.writeValue(response.getWriter(),
                ApiResponse.fail(new ApiError(code.name(), code.getDefaultMessage())));
    }
}
