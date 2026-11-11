package com.mamashop.product.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.mamashop.category.entity.Category;
import com.mamashop.common.exception.NotFoundException;
import com.mamashop.common.response.PageResponse;
import com.mamashop.product.dto.ProductDetailResponse;
import com.mamashop.product.dto.ProductSummaryResponse;
import com.mamashop.product.entity.Product;
import com.mamashop.product.entity.ProductStatus;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

/**
 * 連真的 PostgreSQL 測查詢條件（LIKE、排序、分頁要在真的資料庫上才準）。
 * 本機 dev 資料庫可能有測試商品，所以篩選都限定在這裡建立的分類或特殊關鍵字。
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(ProductService.class)
class ProductServiceTest {

    @Autowired
    private ProductService productService;

    @Autowired
    private TestEntityManager em;

    private Category bags;
    private Category fabrics;

    @BeforeEach
    void setUp() {
        bags = category("測試-包包");
        fabrics = category("測試-布料");
    }

    @Test
    void search_onlyReturnsOnSaleProducts() {
        product(bags, "上架商品", ProductStatus.ON_SALE);
        product(bags, "下架商品", ProductStatus.OFF_SALE);

        PageResponse<ProductSummaryResponse> result = productService.searchOnSaleProducts(bags.getId(), null, 1, 10);

        assertThat(result.items()).extracting(ProductSummaryResponse::name).containsExactly("上架商品");
    }

    @Test
    void search_filtersByCategory() {
        product(bags, "托特包", ProductStatus.ON_SALE);
        product(fabrics, "棉麻布", ProductStatus.ON_SALE);

        PageResponse<ProductSummaryResponse> result = productService.searchOnSaleProducts(fabrics.getId(), null, 1, 10);

        assertThat(result.items()).extracting(ProductSummaryResponse::name).containsExactly("棉麻布");
    }

    @Test
    void search_keywordIsCaseInsensitive_andCombinesWithCategory() {
        product(bags, "ZZTEST Canvas Tote", ProductStatus.ON_SALE);
        product(fabrics, "ZZTEST Canvas Fabric", ProductStatus.ON_SALE);

        PageResponse<ProductSummaryResponse> result = productService.searchOnSaleProducts(bags.getId(), "  canvas  ", 1, 10);

        assertThat(result.items()).extracting(ProductSummaryResponse::name).containsExactly("ZZTEST Canvas Tote");
    }

    @Test
    void search_keywordWildcardsAreEscaped() {
        product(bags, "ZZTEST 100% 純棉", ProductStatus.ON_SALE);
        product(bags, "ZZTEST 1000 號帆布", ProductStatus.ON_SALE);

        // "100%" 應該只找到字面上含 "100%" 的商品，不能把 % 當萬用字元
        PageResponse<ProductSummaryResponse> result = productService.searchOnSaleProducts(null, "100%", 1, 10);

        assertThat(result.items()).extracting(ProductSummaryResponse::name).containsExactly("ZZTEST 100% 純棉");
    }

    @Test
    void search_paginatesNewestFirst_withOneBasedPageNumber() {
        for (int i = 1; i <= 5; i++) {
            product(bags, "商品" + i, ProductStatus.ON_SALE);
        }

        PageResponse<ProductSummaryResponse> page1 = productService.searchOnSaleProducts(bags.getId(), null, 1, 2);
        PageResponse<ProductSummaryResponse> page3 = productService.searchOnSaleProducts(bags.getId(), null, 3, 2);

        assertThat(page1.page()).isEqualTo(1);
        assertThat(page1.totalElements()).isEqualTo(5);
        assertThat(page1.totalPages()).isEqualTo(3);
        assertThat(page1.items()).extracting(ProductSummaryResponse::name).containsExactly("商品5", "商品4");
        assertThat(page3.items()).extracting(ProductSummaryResponse::name).containsExactly("商品1");
    }

    @Test
    void detail_includesCategoryName() {
        Product p = product(bags, "托特包", ProductStatus.ON_SALE);
        em.clear(); // 確保從資料庫重新查詢，驗證 @EntityGraph 有一起載入分類

        ProductDetailResponse detail = productService.getOnSaleProduct(p.getId());

        assertThat(detail.name()).isEqualTo("托特包");
        assertThat(detail.categoryName()).isEqualTo("測試-包包");
        assertThat(detail.price()).isEqualByComparingTo("890.00");
    }

    @Test
    void detail_offSaleProduct_throwsNotFound() {
        Product p = product(bags, "下架商品", ProductStatus.OFF_SALE);

        assertThatThrownBy(() -> productService.getOnSaleProduct(p.getId()))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void detail_nonExistingProduct_throwsNotFound() {
        assertThatThrownBy(() -> productService.getOnSaleProduct(Long.MAX_VALUE))
                .isInstanceOf(NotFoundException.class);
    }

    private Category category(String name) {
        Category c = new Category();
        c.setName(name);
        return em.persist(c);
    }

    private Product product(Category category, String name, ProductStatus status) {
        Product p = new Product();
        p.setCategory(category);
        p.setName(name);
        p.setPrice(new BigDecimal("890.00"));
        p.setStock(5);
        p.setStatus(status);
        em.persist(p);
        em.flush();
        return p;
    }
}
