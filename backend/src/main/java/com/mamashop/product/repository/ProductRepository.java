package com.mamashop.product.repository;

import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import java.time.Instant;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * JpaSpecificationExecutor：支援用 {@link ProductSpecifications} 組合動態查詢條件
 * （分類、關鍵字都是選填，不用為每種組合各寫一個方法）。
 */
public interface ProductRepository extends JpaRepository<Product, Long>, JpaSpecificationExecutor<Product> {

    /**
     * 覆寫 JpaSpecificationExecutor 的分頁查詢，加上 @EntityGraph 一起 JOIN 分類：
     * 後台列表要顯示分類名稱，不 JOIN 的話每個商品會各觸發一次分類查詢（N+1）。
     * （多對一的 JOIN 不會增加資料筆數，分頁仍在資料庫做）
     */
    @Override
    @EntityGraph(attributePaths = "category")
    Page<Product> findAll(Specification<Product> spec, Pageable pageable);

    /**
     * 商品詳情：同時 JOIN 分類（@EntityGraph），一次查詢就拿到分類名稱，
     * 避免 LAZY 關聯在之後才觸發第二次查詢。
     */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findByIdAndStatus(Long id, ProductStatus status);

    /** 後台：不限狀態 */
    @EntityGraph(attributePaths = "category")
    Optional<Product> findWithCategoryById(Long id);

    boolean existsByCategoryId(Long categoryId);

    /**
     * 後台調整庫存（增減量，不是直接設定數字）。
     *
     * <p>用一條 UPDATE 在資料庫裡完成「讀取＋計算＋寫回」，條件不符（扣到負數）就更新 0 筆。
     * 如果改成「先讀出庫存、在 Java 算好、再寫回」，期間顧客下單扣掉的庫存會被覆蓋（lost update）。
     *
     * @return 更新筆數：1 成功；0 代表商品不存在或調整後會小於 0
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("""
            UPDATE Product p
               SET p.stock = p.stock + :delta, p.updatedAt = :now
             WHERE p.id = :id AND p.stock + :delta >= 0
            """)
    int adjustStock(@Param("id") Long id, @Param("delta") int delta, @Param("now") Instant now);
}
