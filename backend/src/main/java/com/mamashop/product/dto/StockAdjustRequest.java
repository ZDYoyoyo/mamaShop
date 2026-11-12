package com.mamashop.product.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 調整庫存：正數是進貨（+5），負數是盤點減少（-2）。
 * 用增減量而不是「設定成某個數字」，原因見 ProductRepository.adjustStock。
 */
public record StockAdjustRequest(@NotNull @Min(-100_000) @Max(100_000) Integer delta) {
}
