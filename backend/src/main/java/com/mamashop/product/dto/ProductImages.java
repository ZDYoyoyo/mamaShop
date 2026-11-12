package com.mamashop.product.dto;

import com.mamashop.common.config.WebConfig;

/** 資料庫存的是相對路徑（products/xxx.jpg），回傳給前端時轉成網址（/uploads/products/xxx.jpg）。 */
final class ProductImages {

    private ProductImages() {
    }

    static String toUrl(String imagePath) {
        return imagePath == null ? null : WebConfig.UPLOAD_URL_PREFIX + imagePath;
    }
}
