package com.mamashop.category.dto;

import com.mamashop.category.entity.Category;

/** 後台用：比前台多了排序欄位 */
public record AdminCategoryResponse(Long id, String name, int sortOrder) {

    public static AdminCategoryResponse from(Category category) {
        return new AdminCategoryResponse(category.getId(), category.getName(), category.getSortOrder());
    }
}
