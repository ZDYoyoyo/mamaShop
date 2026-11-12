package com.mamashop.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 修改商品基本資料。
 *
 * <p>刻意不包含 stock 和 status：
 * <ul>
 *   <li>庫存用 PATCH /stock 以「增減量」調整，避免覆蓋掉同時間顧客下單扣掉的數量</li>
 *   <li>上下架用 PATCH /status，是獨立、常用的操作</li>
 * </ul>
 */
public record ProductUpdateRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 5000) String description,
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price) {

    public ProductUpdateRequest {
        name = name == null ? null : name.trim();
    }
}
