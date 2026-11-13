package com.mamashop.dashboard.dto;

import com.mamashop.product.entity.Product;
import java.util.List;

/**
 * 後台首頁概覽（A05）。
 *
 * @param todayOrders        今天（台灣時間）新進的訂單數，包含之後被取消的
 * @param pendingConfirm     待確認：要聯絡顧客、確認訂單
 * @param awaitingShipment   備貨中：已確認、等待出貨
 * @param awaitingPickup     超商取貨、已到門市但顧客還沒取件（狀態「已送達」）
 * @param returnedRecently   最近 {@code returnedDays} 天內退回的訂單
 * @param returnedDays       「最近」是幾天
 * @param lowStockCount      上架中、庫存小於等於 {@code lowStockThreshold} 的商品數（含售完）
 * @param lowStockThreshold  低庫存的門檻（和前台顯示「僅剩 N 件」一致）
 * @param lowStockProducts   低庫存商品，庫存少的在前，最多 20 筆
 */
public record AdminDashboardResponse(
        long todayOrders,
        long pendingConfirm,
        long awaitingShipment,
        long awaitingPickup,
        long returnedRecently,
        int returnedDays,
        long lowStockCount,
        int lowStockThreshold,
        List<LowStockProduct> lowStockProducts) {

    public record LowStockProduct(Long id, String name, int stock) {
        public static LowStockProduct from(Product product) {
            return new LowStockProduct(product.getId(), product.getName(), product.getStock());
        }
    }
}
