package com.mamashop.product.controller;

import com.mamashop.common.response.ApiResponse;
import com.mamashop.common.response.PageResponse;
import com.mamashop.product.dto.AdminProductResponse;
import com.mamashop.product.dto.ProductCreateRequest;
import com.mamashop.product.dto.ProductStatusRequest;
import com.mamashop.product.dto.ProductUpdateRequest;
import com.mamashop.product.dto.StockAdjustRequest;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.service.AdminProductService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/** 後台商品管理。商品不提供刪除：舊訂單明細會參照商品，不再販售就下架。 */
@RestController
@RequestMapping("/api/admin/products")
public class AdminProductController {

    private final AdminProductService adminProductService;

    public AdminProductController(AdminProductService adminProductService) {
        this.adminProductService = adminProductService;
    }

    /** 商品列表（含下架）。例如：GET /api/admin/products?status=OFF_SALE&page=1 */
    @GetMapping
    public ApiResponse<PageResponse<AdminProductResponse>> list(
            @RequestParam(required = false) @Positive Long categoryId,
            @RequestParam(required = false) @Size(max = 50) String keyword,
            @RequestParam(required = false) ProductStatus status,
            @RequestParam(defaultValue = "1") @Min(1) int page,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int size) {
        return ApiResponse.ok(adminProductService.search(categoryId, keyword, status, page, size));
    }

    @GetMapping("/{id}")
    public ApiResponse<AdminProductResponse> get(@PathVariable @Positive Long id) {
        return ApiResponse.ok(adminProductService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminProductResponse> create(@Valid @RequestBody ProductCreateRequest request) {
        return ApiResponse.ok(adminProductService.create(request));
    }

    /** 修改基本資料（分類、名稱、描述、價格） */
    @PutMapping("/{id}")
    public ApiResponse<AdminProductResponse> update(@PathVariable @Positive Long id,
                                                    @Valid @RequestBody ProductUpdateRequest request) {
        return ApiResponse.ok(adminProductService.update(id, request));
    }

    /** 上架／下架：{"status": "ON_SALE"} */
    @PatchMapping("/{id}/status")
    public ApiResponse<AdminProductResponse> changeStatus(@PathVariable @Positive Long id,
                                                          @Valid @RequestBody ProductStatusRequest request) {
        return ApiResponse.ok(adminProductService.changeStatus(id, request.status()));
    }

    /** 調整庫存：{"delta": 5} 進貨 5 件、{"delta": -2} 減少 2 件 */
    @PatchMapping("/{id}/stock")
    public ApiResponse<AdminProductResponse> adjustStock(@PathVariable @Positive Long id,
                                                         @Valid @RequestBody StockAdjustRequest request) {
        return ApiResponse.ok(adminProductService.adjustStock(id, request.delta()));
    }

    /** 上傳／更換商品圖片（multipart/form-data，欄位名稱 file） */
    @PostMapping(value = "/{id}/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<AdminProductResponse> uploadImage(@PathVariable @Positive Long id,
                                                         @RequestPart("file") MultipartFile file) {
        return ApiResponse.ok(adminProductService.uploadImage(id, file));
    }
}
