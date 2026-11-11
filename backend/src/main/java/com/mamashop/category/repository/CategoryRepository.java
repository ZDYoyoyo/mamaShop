package com.mamashop.category.repository;

import com.mamashop.category.entity.Category;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    /** Spring Data 依方法名稱產生查詢：ORDER BY sort_order ASC, id ASC */
    List<Category> findAllByOrderBySortOrderAscIdAsc();
}
