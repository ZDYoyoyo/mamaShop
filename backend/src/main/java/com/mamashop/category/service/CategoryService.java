package com.mamashop.category.service;

import com.mamashop.category.dto.AdminCategoryResponse;
import com.mamashop.category.dto.CategoryRequest;
import com.mamashop.category.dto.CategoryResponse;
import com.mamashop.category.entity.Category;
import com.mamashop.category.repository.CategoryRepository;
import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.product.cache.ProductCacheEvictor;
import com.mamashop.product.repository.ProductRepository;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ProductRepository productRepository;
    private final ProductCacheEvictor productCacheEvictor;

    // 只有一個建構子時，Spring 會自動用它注入，不用加 @Autowired
    public CategoryService(CategoryRepository categoryRepository, ProductRepository productRepository,
                           ProductCacheEvictor productCacheEvictor) {
        this.categoryRepository = categoryRepository;
        this.productRepository = productRepository;
        this.productCacheEvictor = productCacheEvictor;
    }

    // ===== 前台 =====

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(CategoryResponse::from)
                .toList();
    }

    // ===== 後台 =====

    @Transactional(readOnly = true)
    public List<AdminCategoryResponse> listForAdmin() {
        return categoryRepository.findAllByOrderBySortOrderAscIdAsc().stream()
                .map(AdminCategoryResponse::from)
                .toList();
    }

    @Transactional
    public AdminCategoryResponse create(CategoryRequest request) {
        if (categoryRepository.existsByName(request.name())) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
        Category category = new Category();
        category.setName(request.name());
        category.setSortOrder(request.sortOrderOrDefault());
        return AdminCategoryResponse.from(saveAndFlush(category));
    }

    @Transactional
    public AdminCategoryResponse update(Long id, CategoryRequest request) {
        Category category = findOrThrow(id);
        if (categoryRepository.existsByNameAndIdNot(request.name(), id)) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
        category.setName(request.name());
        category.setSortOrder(request.sortOrderOrDefault());
        // category 是這個交易裡查出來的（managed 狀態），其實交易結束時 Hibernate 就會比對差異自動 UPDATE
        // （dirty checking）。這裡仍呼叫 saveAndFlush，是為了在方法內就送出 UPDATE、接住唯一鍵衝突
        AdminCategoryResponse response = AdminCategoryResponse.from(saveAndFlush(category));
        // 商品詳情裡有分類名稱：這個分類底下的商品都要更新，直接全部清除（分類很少改）
        productCacheEvictor.evictAll();
        return response;
    }

    /** 只能刪除沒有商品的分類（products.category_id 有外鍵，硬刪也會失敗） */
    @Transactional
    public void delete(Long id) {
        Category category = findOrThrow(id);
        if (productRepository.existsByCategoryId(id)) {
            throw new BusinessException(ErrorCode.CATEGORY_HAS_PRODUCTS);
        }
        categoryRepository.delete(category);
    }

    private Category findOrThrow(Long id) {
        return categoryRepository.findById(id).orElseThrow(() -> new NotFoundException("找不到分類"));
    }

    /** 兩個人同時新增同名分類時，由資料庫的 UNIQUE 擋下 */
    private Category saveAndFlush(Category category) {
        try {
            return categoryRepository.saveAndFlush(category);
        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(ErrorCode.CATEGORY_NAME_EXISTS);
        }
    }
}
