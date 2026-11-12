package com.mamashop.product.dto;

import com.mamashop.product.entity.ProductStatus;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

/**
 * 新增商品。
 *
 * @param status 選填，沒填就是下架（OFF_SALE）：建好、上傳圖片、確認無誤後再上架
 */
public record ProductCreateRequest(
        @NotNull @Positive Long categoryId,
        @NotBlank @Size(max = 100) String name,
        @Size(max = 5000) String description,
        // 對應資料庫 NUMERIC(10,2)：整數最多 8 位、小數最多 2 位
        @NotNull @DecimalMin("0") @Digits(integer = 8, fraction = 2) BigDecimal price,
        @NotNull @Min(0) @Max(100_000) Integer stock,
        ProductStatus status) {

    public ProductCreateRequest {
        name = name == null ? null : name.trim();
    }
}
