package com.mamashop.product.dto;

import com.mamashop.product.entity.ProductStatus;
import jakarta.validation.constraints.NotNull;

/** 上架／下架 */
public record ProductStatusRequest(@NotNull ProductStatus status) {
}
