package com.mamashop.category.controller;

import com.mamashop.category.dto.AdminCategoryResponse;
import com.mamashop.category.dto.CategoryRequest;
import com.mamashop.category.service.CategoryService;
import com.mamashop.common.response.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Positive;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** 後台分類管理。/api/admin/** 在 SecurityConfig 限定 ADMIN。 */
@RestController
@RequestMapping("/api/admin/categories")
public class AdminCategoryController {

    private final CategoryService categoryService;

    public AdminCategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    @GetMapping
    public ApiResponse<List<AdminCategoryResponse>> list() {
        return ApiResponse.ok(categoryService.listForAdmin());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminCategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<AdminCategoryResponse> update(@PathVariable @Positive Long id,
                                                     @Valid @RequestBody CategoryRequest request) {
        return ApiResponse.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable @Positive Long id) {
        categoryService.delete(id);
        return ApiResponse.ok();
    }
}
