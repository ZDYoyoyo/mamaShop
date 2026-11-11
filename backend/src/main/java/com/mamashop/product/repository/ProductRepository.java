package com.mamashop.product.repository;

import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * JpaSpecificationExecutor：支援用 {@link ProductSpecifications} 組合動態查詢條件
 * （分類、關鍵字都是選填，不用為每種組合各寫一個方法）。
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 商品詳情：同時 JOIN 分類（@EntityGraph），一次查詢就拿到分類名稱，
     * 避免 LAZY 關聯在之後才觸發第二次查詢。
     */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);
}
