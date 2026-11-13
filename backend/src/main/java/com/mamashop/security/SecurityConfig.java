package com.mamashop.security;

import com.mamashop.user.repository.UserRepository;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

@Configuration
@EnableConfigurationProperties({JwtProperties.class, CorsProperties.class})
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtService jwtService,
                                                   UserRepository userRepository,
                                                   SecurityErrorWriter errorWriter) throws Exception {
        http
                // CSRF 攻擊是利用瀏覽器「自動帶 cookie」的特性。這裡的 token 放在 Authorization header，
                // 瀏覽器不會自動帶，所以不需要 CSRF token。（如果改用 cookie 存 token 就必須打開）
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                // 不建立 HttpSession：每個請求都靠 JWT 認人，伺服器不記住登入狀態，之後要開多台也不用共享 session
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                // 關掉預設的登入頁、Basic 認證、登出頁，改用自己的 /api/auth/login
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .authorizeHttpRequests(auth -> auth
                        // 註冊、登入
                        .requestMatchers(HttpMethod.POST, "/api/auth/register", "/api/auth/login").permitAll()
                        // 前台瀏覽商品不用登入（只開放 GET）
                        .requestMatchers(HttpMethod.GET,
                                "/api/categories/**", "/api/products/**", "/api/shipping-methods/**").permitAll()
                        // 商品圖片（B07）
                        .requestMatchers(HttpMethod.GET, "/uploads/**").permitAll()
                        // API 文件（B13）。是否啟用由 API_DOCS_ENABLED 控制，正式環境關閉時這些路徑會是 404
                        .requestMatchers(HttpMethod.GET, "/v3/api-docs/**", "/swagger-ui/**", "/swagger-ui.html").permitAll()
                        // 健康檢查（D07）：給 Docker 與外部監控服務用，不需要登入。
                        // 只開放 health 這一個；其他 actuator 端點沒有暴露（application.yml），就算暴露了也要登入
                        .requestMatchers(HttpMethod.GET, "/api/actuator/health").permitAll()
                        // 例外發生時 Spring Boot 會轉到 /error；不開放的話真正的錯誤會被蓋成 401
                        .requestMatchers("/error").permitAll()
                        // 後台
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // 其他全部要登入（預設拒絕：新增 API 忘了設定時，是被擋而不是被公開）
                        .anyRequest().authenticated())
                .exceptionHandling(e -> e
                        .authenticationEntryPoint(errorWriter)
                        .accessDeniedHandler(errorWriter))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    /**
     * 密碼雜湊。DelegatingPasswordEncoder 會在雜湊前面加上演算法名稱，例如 {bcrypt}$2a$10$...，
     * 之後要換演算法時，舊密碼仍然能驗證，不用強迫所有人重設密碼。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return PasswordEncoderFactories.createDelegatingPasswordEncoder();
    }

    /**
     * CORS：本機開發時前端（localhost:5173）和後端（localhost:8080）不同來源，
     * 瀏覽器預設會擋。這裡列出允許的前端網址。
     * 正式環境前後端都經過同一個 Nginx（同網域），其實不會觸發 CORS。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(CorsProperties corsProperties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(corsProperties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        // Idempotency-Key：防重複下單（R02）
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Idempotency-Key"));
        // token 放在 header 不是 cookie，所以不需要 allowCredentials
        config.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/api/**", config);
        return source;
    }
}
