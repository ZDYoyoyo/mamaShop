package com.mamashop.user.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.user.dto.AdminAccountResponse;
import com.mamashop.user.dto.CreateAdminRequest;
import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import java.time.Instant;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 管理員帳號管理（B14）：每位家人一個帳號，可以新增、停用、重設密碼。
 *
 * <ul>
 *   <li>只處理 ADMIN 帳號：顧客帳號的 id 一律當作「找不到」</li>
 *   <li>停用而不刪除：狀態紀錄（order_status_histories）還參照這個帳號，刪了就不知道是誰操作的</li>
 *   <li>不能停用自己：避免把自己鎖在外面，也保證至少留下一個能登入的管理員</li>
 *   <li>停用、重設密碼都立即生效：JwtAuthenticationFilter 每次請求都會檢查帳號狀態與密碼修改時間</li>
 * </ul>
 */
@Service
public class AdminAccountService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Transactional(readOnly = true)
    public List<AdminAccountResponse> list() {
        return userRepository.findByRoleOrderByIdAsc(Role.ADMIN).stream()
                .map(AdminAccountResponse::from)
                .toList();
    }

    @Transactional
    public AdminAccountResponse create(CreateAdminRequest request) {
        String email = AuthService.normalizeEmail(request.email());
        if (userRepository.existsByEmail(email)) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(request.password()));
        admin.setName(request.name());
        admin.setPhone(request.phone());
        admin.setRole(Role.ADMIN);
        try {
            return AdminAccountResponse.from(userRepository.saveAndFlush(admin));
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }
    }

    /**
     * 停用或重新啟用管理員。
     *
     * @param operatorId 執行操作的管理員（來自 token），不能停用自己
     */
    @Transactional
    public AdminAccountResponse setEnabled(Long operatorId, Long targetId, boolean enabled) {
        User target = findAdmin(targetId);
        if (!enabled && target.getId().equals(operatorId)) {
            throw new BusinessException(ErrorCode.CANNOT_DISABLE_SELF);
        }
        target.setEnabled(enabled);
        return AdminAccountResponse.from(userRepository.saveAndFlush(target));
    }

    /**
     * 重設密碼。之前發出的 token 全部失效（包括自己的：重設自己的密碼後要重新登入）。
     * 用途：家人忘記密碼、懷疑帳號外洩、上線前更換預設管理員密碼（D08）。
     */
    @Transactional
    public AdminAccountResponse resetPassword(Long targetId, String newPassword) {
        User target = findAdmin(targetId);
        target.changePassword(passwordEncoder.encode(newPassword), Instant.now());
        return AdminAccountResponse.from(userRepository.saveAndFlush(target));
    }

    private User findAdmin(Long id) {
        return userRepository.findByIdAndRole(id, Role.ADMIN)
                .orElseThrow(() -> new NotFoundException("找不到管理員"));
    }
}
