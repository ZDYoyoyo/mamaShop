package com.mamashop.order.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 後台訂單操作（確認、送達、完成、取消）。
 *
 * @param version 畫面上看到的訂單版本號（來自訂單詳情或列表）。
 *                伺服器的版本不同，代表這段時間有人改過訂單 → 回 409，請管理員重新整理再操作。
 * @param note    備註（選填），寫進狀態紀錄，例如「已電話確認」
 */
public record AdminOrderActionRequest(
        @NotNull Long version,
        @Size(max = 255) String note) {
}
