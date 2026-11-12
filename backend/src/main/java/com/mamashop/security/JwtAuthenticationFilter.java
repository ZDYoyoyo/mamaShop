package com.mamashop.security;

import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import org.springframework.http.HttpHeaders;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * 每個請求執行一次：從 Authorization: Bearer xxx 取出 JWT，驗證通過就把使用者放進 SecurityContext。
 *
 * <p>這個 Filter 只負責「認出你是誰」，不負責擋請求：
 * 沒帶 token 或 token 無效時什麼都不做，直接放行，
 * 之後由 SecurityConfig 的路徑權限決定要回 401 還是允許（例如商品列表不用登入）。
 *
 * <p>注意：這個類別刻意不加 @Component。Spring Boot 會把所有 Filter 型別的 Bean
 * 自動註冊到 Servlet 容器，再加上 Security 鏈裡也有一份，就會變成每個請求跑兩次。
 * 所以在 SecurityConfig 裡用 new 建立，只放進 Security 鏈。
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            String token = header.substring(BEARER_PREFIX.length());
            jwtService.parse(token).ifPresent(claims ->
                    // 每次都從資料庫讀會員：帳號被停用、角色被調整、密碼被重設時立刻生效
                    userRepository.findById(claims.userId())
                            .filter(User::isEnabled)
                            .filter(user -> !issuedBeforePasswordChange(claims, user))
                            .ifPresent(this::authenticate));
        }
        chain.doFilter(request, response);
    }

    /**
     * token 是否在密碼修改之前發出（B14：重設密碼後，舊的登入全部失效）。
     * JWT 的 iat 只精確到秒，所以密碼修改時間也取到秒再比較：
     * 同一秒內改密碼又登入的新 token 不會被誤判成舊的。
     */
    private static boolean issuedBeforePasswordChange(JwtService.TokenClaims claims, User user) {
        Instant changedAt = user.getPasswordChangedAt();
        return changedAt != null && claims.issuedAt().isBefore(changedAt.truncatedTo(ChronoUnit.SECONDS));
    }

    private void authenticate(User user) {
        AuthUser principal = AuthUser.from(user);
        var authentication = UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.authorities());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }
}
