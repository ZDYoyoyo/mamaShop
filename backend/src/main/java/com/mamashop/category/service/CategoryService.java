package com.mamashop.category.service;

import com.mamashop.category.dto.CategoryResponse;
import com.mamashop.category.repository.CategoryRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;

    // 只有一個建構子時，Spring 會自動用它注入，不用加 @Autowired
    public CategoryService(CategoryRepository categoryRepository) {
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }
}
