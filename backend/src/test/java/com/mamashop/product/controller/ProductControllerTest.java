package com.mamashop.product.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.mamashop.common.exception.NotFoundException;
import com.mamashop.common.response.PageResponse;
import com.mamashop.product.dto.ProductDetailResponse;
import com.mamashop.product.dto.ProductSummaryResponse;
import com.mamashop.product.service.ProductService;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * 只測 Controller 這一層：參數驗證、預設值、回應格式。
 * Service 用 Mockito 假造（@MockitoBean），不連資料庫。
 */
@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ProductService productService;

    @Test
    void list_usesDefaultPaging_andWrapsResponse() throws Exception {
        when(productService.searchOnSaleProducts(null, null, 1, 12)).thenReturn(new PageResponse<>(
                List.of(new ProductSummaryResponse(1L, "帆布托特包", new BigDecimal("890.00"), 10, null)),
                1, 12, 1, 1));

        mockMvc.perform(get("/api/products"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.items[0].name").value("帆布托特包"))
                .andExpect(jsonPath("$.data.items[0].price").value(890.00))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.totalElements").value(1));
    }

    @Test
    void list_passesFilters() throws Exception {
        when(productService.searchOnSaleProducts(3L, "帆布", 2, 6))
                .thenReturn(new PageResponse<>(List.of(), 2, 6, 0, 0));

        mockMvc.perform(get("/api/products")
                        .param("categoryId", "3").param("keyword", "帆布")
                        .param("page", "2").param("size", "6"))
                .andExpect(status().isOk());

        verify(productService).searchOnSaleProducts(3L, "帆布", 2, 6);
    }

    @Test
    void list_rejectsInvalidPaging() throws Exception {
        mockMvc.perform(get("/api/products").param("page", "0").param("size", "100"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.error.fieldErrors.length()").value(2));

        verify(productService, never()).searchOnSaleProducts(any(), any(), anyInt(), anyInt());
    }

    @Test
    void list_rejectsTooLongKeyword() throws Exception {
        mockMvc.perform(get("/api/products").param("keyword", "a".repeat(51)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.fieldErrors[0].field").value("keyword"));
    }

    @Test
    void detail_returnsProduct() throws Exception {
        when(productService.getOnSaleProduct(1L)).thenReturn(new ProductDetailResponse(
                1L, "帆布托特包", "厚磅帆布", new BigDecimal("890.00"), 10, null, 2L, "手提包"));

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.categoryName").value("手提包"))
                .andExpect(jsonPath("$.data.description").value("厚磅帆布"));
    }

    @Test
    void detail_notFound_returns404() throws Exception {
        when(productService.getOnSaleProduct(99L)).thenThrow(new NotFoundException("找不到商品"));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.message").value("找不到商品"));
    }

    @Test
    void detail_rejectsNonPositiveId() throws Exception {
        mockMvc.perform(get("/api/products/0"))
                .andExpect(status().isBadRequest());
    }
}
