package com.mamashop.user.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import com.mamashop.user.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 純單元測試：用 Mockito 假造 Repository 與 PasswordEncoder，不啟動 Spring、不連資料庫。
 * （ApplicationRunner 在整合測試裡會在交易外執行，資料不會 rollback，所以改用單元測試）
 */
class AdminAccountInitializerTest {

    private final UserRepository userRepository = mock(UserRepository.class);
    private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

    @Test
    void createsAdmin_whenEmailNotExists() {
        when(userRepository.existsByEmail("boss@example.com")).thenReturn(false);
        when(passwordEncoder.encode("S3cret-pass")).thenReturn("{bcrypt}hashed");

        initializer("Boss@Example.com", "S3cret-pass").run(null);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userRepository).save(captor.capture());
        User admin = captor.getValue();
        assertThat(admin.getEmail()).isEqualTo("boss@example.com");
        assertThat(admin.getRole()).isEqualTo(Role.ADMIN);
        assertThat(admin.getPasswordHash()).isEqualTo("{bcrypt}hashed");
    }

    @Test
    void skips_whenAdminAlreadyExists() {
        when(userRepository.existsByEmail("boss@example.com")).thenReturn(true);

        initializer("boss@example.com", "S3cret-pass").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void skips_whenNotConfigured() {
        initializer("", "").run(null);

        verify(userRepository, never()).save(any());
    }

    @Test
    void rejectsShortPassword() {
        when(userRepository.existsByEmail("boss@example.com")).thenReturn(false);

        assertThatThrownBy(() -> initializer("boss@example.com", "short").run(null))
                .isInstanceOf(IllegalStateException.class);
        verify(userRepository, never()).save(any());
    }

    private AdminAccountInitializer initializer(String email, String password) {
        return new AdminAccountInitializer(
                new AdminProperties(email, password, "管理員"), userRepository, passwordEncoder);
    }
}
