package com.mamashop.product.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.common.response.PageResponse;
import com.mamashop.product.dto.ProductDetailResponse;
import com.mamashop.product.dto.ProductSummaryResponse;
import com.mamashop.product.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "前台：商品")
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * 商品列表。例如：GET /api/products?categoryId=1&keyword=帆布&page=1&size=12
     * size 設上限，避免有人一次要 10 萬筆把伺服器拖垮。
     */
    @Operation(summary = "商品列表（分頁、分類、關鍵字；只列上架商品）")
    @GetMapping
    public ApiResponse<PageResponse<ProductSummaryResponse>> list(
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "12") @Min(1) @Max(50) int size) {
        return ApiResponse.ok(productService.searchOnSaleProducts(categoryId, keyword, page, size));
    }

    /** 商品詳情 */
    @Operation(summary = "商品詳情（下架商品回 404）")
    @GetMapping("/{id}")
    public ApiResponse<ProductDetailResponse> detail(@PathVariable @Positive Long id) {
        return ApiResponse.ok(productService.getOnSaleProduct(id));
    }
}
