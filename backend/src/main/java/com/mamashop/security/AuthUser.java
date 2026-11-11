package com.mamashop.security;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import java.util.List;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

/**
 * 目前登入的使用者，放在 SecurityContext 裡。
 * Controller 用 {@code @AuthenticationPrincipal AuthUser user} 取得，
 * 例如 B09 用 user.id() 確保顧客只能查自己的訂單。
 */
public record AuthUser(Long id, String email, Role role) {

    public static AuthUser from(User user) {
        return new AuthUser(user.getId(), user.getEmail(), user.getRole());
    }

    /** Spring Security 的角色慣例是加上 ROLE_ 前綴，hasRole("ADMIN") 會比對 ROLE_ADMIN */
    public List<GrantedAuthority> authorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_" + role.name()));
    }
}
