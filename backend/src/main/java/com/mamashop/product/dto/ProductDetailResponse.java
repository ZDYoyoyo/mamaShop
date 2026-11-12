package com.mamashop.product.dto;

import com.mamashop.product.entity.Product;
import java.math.BigDecimal;

/** 商品詳情：呼叫前 category 必須已載入（Repository 用 @EntityGraph 一起查出來）。 */
public record ProductDetailResponse(
        Long id,
        String name,
        String description,
        BigDecimal price,
        int stock,
        String imageUrl,
        Long categoryId,
        String categoryName) {

    public static ProductDetailResponse from(Product product) {
        return new ProductDetailResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStock(),
                ProductImages.toUrl(product.getImagePath()),
                product.getCategory().getId(),
                product.getCategory().getName());
    }
}
