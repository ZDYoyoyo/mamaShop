package com.mamashop.user.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.security.JwtService;
import com.mamashop.user.dto.LoginRequest;
import com.mamashop.user.dto.LoginResponse;
import com.mamashop.user.dto.RegisterRequest;
import com.mamashop.user.dto.UserResponse;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Optional;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
public class AuthService {

    /** BCrypt 只處理前 72 bytes，超過的直接視為錯誤密碼 */
    private static final int BCRYPT_MAX_BYTES = 72;

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * 帳號不存在時，也拿這個假雜湊跑一次比對，讓回應時間和「密碼錯誤」差不多，
     * 避免有人從回應快慢猜出哪些 Email 有註冊（timing attack）。
     */
    private final String dummyPasswordHash;

    public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.dummyPasswordHash = passwordEncoder.encode("dummy-password-for-timing");
    }

    @Transactional
    public UserResponse register(RegisterRequest request) {
        String email = normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        User user = new User();
        user.setEmail(email);
        user.setPasswordHash(passwordEncoder.encode(request.password()));
        user.setName(request.name().trim());
        user.setPhone(StringUtils.hasText(request.phone()) ? request.phone() : null);
        user.setRole(Role.USER); // 註冊一律是顧客，管理員只能由管理員建立（B14）

        try {
            // saveAndFlush：立刻送出 INSERT，才能在這裡接到唯一鍵衝突
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException e) {
            // 兩個人同時用同一個 Email 註冊：上面的 exists 檢查都通過，靠資料庫的 UNIQUE 擋下其中一個
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        return UserResponse.from(user);
    }

    @Transactional(readOnly = true)
    public LoginResponse login(LoginRequest request) {
        if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        Optional<User> found = userRepository.findByEmail(normalizeEmail(request.email()));
        if (found.isEmpty()) {
            passwordEncoder.matches(request.password(), dummyPasswordHash);
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        User user = found.get();
        // 停用的帳號也回同一個訊息，不透露帳號狀態
        if (!passwordEncoder.matches(request.password(), user.getPasswordHash()) || !user.isEnabled()) {
            throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
        }

        String token = jwtService.generateToken(user);
        return new LoginResponse(token, "Bearer", jwtService.getExpiration().toSeconds(), UserResponse.from(user));
    }

    /** Email 不分大小寫：統一去除前後空白並轉小寫後再存、再查 */
    public static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
