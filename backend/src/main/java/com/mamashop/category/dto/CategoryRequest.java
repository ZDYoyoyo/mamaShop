package com.mamashop.category.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** 新增／修改分類 */
public record CategoryRequest(
        @NotBlank @Size(max = 50) String name,
        // 選填，數字小的排前面；沒填就是 0
        @Min(0) Integer sortOrder) {

    public CategoryRequest {
        name = name == null ? null : name.trim();
    }

    public int sortOrderOrDefault() {
        return sortOrder == null ? 0 : sortOrder;
    }
}
