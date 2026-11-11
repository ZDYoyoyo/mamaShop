package com.mamashop.product.dto;

import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import java.math.BigDecimal;
import java.time.Instant;

/** 後台商品資料：比前台多了狀態與時間。呼叫前 category 必須已載入（@EntityGraph）。 */
public record AdminProductResponse(
        Long id,
        Long categoryId,
        String categoryName,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String imageUrl,
        ProductStatus status,
        Instant createdAt,
        Instant updatedAt) {

    public static AdminProductResponse from(Product product) {
        return new AdminProductResponse(
                product.getId(),
                product.getCategory().getId(),
                product.getCategory().getName(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                ProductImages.toUrl(product.getImagePath()),
                product.getStatus(),
                product.getCreatedAt(),
                product.getUpdatedAt());
    }
}
