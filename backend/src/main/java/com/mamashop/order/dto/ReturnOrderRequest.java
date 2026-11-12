package com.mamashop.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 退回（例如超商未取件、包裹退回）。
 *
 * @param restock 是否把商品加回庫存。由管理員決定：退回的商品可能已經損壞，不一定能再賣
 */
public record ReturnOrderRequest(
        @NotNull Long version,
        @NotNull Boolean restock,
        @Size(max = 255) String note) {
}
