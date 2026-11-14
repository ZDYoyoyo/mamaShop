package com.mamashop.product.service;

import static com.mamashop.product.repository.ProductSpecifications.hasStatus;
import static com.mamashop.product.repository.ProductSpecifications.inCategory;
import static com.mamashop.product.repository.ProductSpecifications.nameContains;

import com.mamashop.common.exception.NotFoundException;
import com.mamashop.common.response.PageResponse;
import com.mamashop.product.cache.ProductCacheNames;
import com.mamashop.product.dto.ProductDetailResponse;
import com.mamashop.product.dto.ProductSummaryResponse;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

/** 前台商品查詢：只會看到上架中（ON_SALE）的商品。 */
@Service
public class ProductService {

    /** 新上架的排前面；建立時間相同時用 id 排，確保分頁結果穩定（不會換頁時重複或漏掉） */
    private static final Sort NEWEST_FIRST = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("id"));

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * @param categoryId 分類（選填）
     * @param keyword    商品名稱關鍵字（選填，空白視為沒填）
     * @param page       頁碼，從 1 開始
     * @param size       每頁筆數
     *
     * <p>快取（R01）：沒有關鍵字的列表存 Redis，key 是「分類:頁碼:每頁筆數」，例如 all:1:12、3:2:12。
     * 關鍵字搜尋不快取：關鍵字可以是任何字，每個都存會塞滿 Redis，命中率也低。
     * 第一次查資料庫、之後直接從 Redis 拿（這個方法不會執行）；商品變動時由 ProductCacheEvictor 清除。
     */
    @Cacheable(cacheNames = ProductCacheNames.LIST,
            key = "(#categoryId ?: 'all') + ':' + #page + ':' + #size",
            condition = "#keyword == null || #keyword.isBlank()")
    @Transactional(readOnly = true)
    public PageResponse<ProductSummaryResponse> searchOnSaleProducts(Long categoryId, String keyword, int page, int size) {
        Specification<Product> spec = hasStatus(ProductStatus.ON_SALE);
        if (categoryId != null) {
            spec = spec.and(inCategory(categoryId));
        }
        if (StringUtils.hasText(keyword)) {
            spec = spec.and(nameContains(keyword.trim()));
        }

        // Spring Data 的頁碼從 0 開始，API 從 1 開始，在這裡轉換
        Page<Product> result = productRepository.findAll(spec, PageRequest.of(page - 1, size, NEWEST_FIRST));
        return PageResponse.of(result, ProductSummaryResponse::from);
    }

    /**
     * 商品詳情；下架或不存在的商品一律回 404（不讓顧客看到下架商品）。
     * 快取（R01）：key 是商品 id。找不到時丟例外，例外不會被快取。
     */
    @Cacheable(cacheNames = ProductCacheNames.DETAIL, key = "#id")
    @Transactional(readOnly = true)
    public ProductDetailResponse getOnSaleProduct(Long id) {
        return productRepository.findByIdAndStatus(id, ProductStatus.ON_SALE)
                .map(ProductDetailResponse::from)
                .orElseThrow(() -> new NotFoundException("找不到商品"));
    }
}
