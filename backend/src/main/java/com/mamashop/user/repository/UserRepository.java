package com.mamashop.user.repository;

import com.mamashop.user.entity.Role;
import com.mamashop.user.entity.User;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<User, Long> {

    /** email 一律先轉小寫再查（見 AuthService.normalizeEmail） */
    Optional<User> findByEmail(String email);

    boolean existsByEmail(String email);

    /** 後台管理員列表 */
    List<User> findByRoleOrderByIdAsc(Role role);

    Optional<User> findByIdAndRole(Long id, Role role);
}
