package com.mamashop.product.dto;

import com.mamashop.product.entity.Product;
import java.math.BigDecimal;

/**
 * 商品列表的每一筆：只放列表畫面需要的欄位（不含描述），回應比較小。
 * 也不讀取 category，避免列表的每個商品各觸發一次分類查詢（N+1）。
 */
public record ProductSummaryResponse(Long id, String name, BigDecimal price, int stock, String imageUrl) {

    public static ProductSummaryResponse from(Product product) {
        return new ProductSummaryResponse(
                product.getId(),
                product.getName(),
                product.getPrice(),
                product.getStock(),
                // 圖片網址的格式在 B07（圖片上傳）決定，目前直接回傳 image_path
                product.getImagePath());
    }
}
