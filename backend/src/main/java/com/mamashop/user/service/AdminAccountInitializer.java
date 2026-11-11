package com.mamashop.user.service;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

/**
 * 應用程式啟動完成後執行一次（ApplicationRunner）：建立預設管理員。
 *
 * <ul>
 *   <li>只在該 Email 還不存在時建立；已存在就不動，不會覆蓋之後改過的密碼</li>
 *   <li>沒設定 ADMIN_EMAIL／ADMIN_PASSWORD 就略過</li>
 *   <li>上線後請登入改密碼（D08），之後可以從環境變數移除 ADMIN_PASSWORD</li>
 * </ul>
 */
@Component
@EnableConfigurationProperties(AdminProperties.class)
public class AdminAccountInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminAccountInitializer.class);
    private static final int MIN_PASSWORD_LENGTH = 8;

    private final AdminProperties properties;
    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminAccountInitializer(AdminProperties properties, UserRepository userRepository,
                                   PasswordEncoder passwordEncoder) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!StringUtils.hasText(properties.email()) || !StringUtils.hasText(properties.password())) {
            log.info("未設定 ADMIN_EMAIL／ADMIN_PASSWORD，略過建立預設管理員");
            return;
        }

        String email = AuthService.normalizeEmail(properties.email());
        if (userRepository.existsByEmail(email)) {
            log.info("預設管理員 {} 已存在，略過建立", email);
            return;
        }

        if (properties.password().length() < MIN_PASSWORD_LENGTH) {
            // 寧可啟動失敗，也不要建立一個弱密碼的管理員
            throw new IllegalStateException("ADMIN_PASSWORD 至少需要 " + MIN_PASSWORD_LENGTH + " 個字元");
        }

        User admin = new User();
        admin.setEmail(email);
        admin.setPasswordHash(passwordEncoder.encode(properties.password()));
        admin.setName(StringUtils.hasText(properties.name()) ? properties.name() : "管理員");
        admin.setRole(Role.ADMIN);
        userRepository.save(admin);
        log.info("已建立預設管理員 {}", email);
    }
}
