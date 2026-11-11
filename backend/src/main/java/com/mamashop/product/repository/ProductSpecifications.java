package com.mamashop.product.repository;

import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import org.springframework.data.jpa.domain.Specification;

/**
 * 商品查詢條件。每個方法回傳一個 WHERE 條件，Service 依參數有沒有值決定要不要組合進去。
 * 例如：onSale().and(inCategory(3)).and(nameContains("帆布"))
 */
public final class ProductSpecifications {

    private static final char ESCAPE = '\\';

    private ProductSpecifications() {
    }

    public static Specification<Product> hasStatus(ProductStatus status) {
        return (root, query, cb) -> cb.equal(root.get("status"), status);
    }

    public static Specification<Product> inCategory(Long categoryId) {
        // 比對外鍵欄位，不會 JOIN categories 表
        return (root, query, cb) -> cb.equal(root.get("category").get("id"), categoryId);
    }

    /** 商品名稱包含關鍵字（不分大小寫）。 */
    public static Specification<Product> nameContains(String keyword) {
        String pattern = "%" + escapeLike(keyword.toLowerCase()) + "%";
        return (root, query, cb) -> cb.like(cb.lower(root.get("name")), pattern, ESCAPE);
    }

    /**
     * 跳脫 LIKE 的萬用字元：使用者輸入 "100%" 時，要找的是字面上的 "100%"，
     * 不是「100 開頭的任何字」。
     */
    static String escapeLike(String value) {
        return value
                .replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
