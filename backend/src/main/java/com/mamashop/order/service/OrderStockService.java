package com.mamashop.order.service;

import com.mamashop.common.exception.BusinessException;
import com.mamashop.common.exception.ErrorCode;
import com.mamashop.order.entity.Order;
import com.mamashop.product.entity.ProductStatus;
import com.mamashop.product.repository.ProductRepository;
import java.time.Instant;
import java.util.Comparator;
import java.util.SortedMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 訂單的庫存異動：下單扣庫存、取消歸還庫存。
 *
 * <p>兩個方法都標 {@code Propagation.MANDATORY}：必須在呼叫端已經開好的交易裡執行，
 * 沒有交易就直接報錯。庫存異動一定要和訂單的寫入同生共死，不能單獨 commit。
 *
 * <p>兩個方法都依商品 id 由小到大處理，原因見 {@link #deduct}。
 */
@Service
public class OrderStockService {

    private final ProductRepository productRepository;

    public OrderStockService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * 下單扣庫存（B08）。任何一個商品失敗就丟出例外，整筆交易回滾。
     *
     * <p>為什麼要依 id 排序：避免死結（deadlock）。
     * 假設訂單 A 買「商品1、商品2」，訂單 B 買「商品2、商品1」，兩者同時執行：
     * A 鎖住商品1、B 鎖住商品2，接著 A 等商品2、B 等商品1 → 互相等待，永遠卡住
     * （PostgreSQL 偵測到後會讓其中一筆失敗）。大家都照同樣順序上鎖，就不可能形成這種循環等待。
     *
     * @param quantities 商品 id → 數量（SortedMap 保證依 id 排序）
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public void deduct(SortedMap<Long, Integer> quantities) {
        Instant now = Instant.now();
        quantities.forEach((productId, quantity) -> {
            int updated = productRepository.decreaseStock(productId, quantity, ProductStatus.ON_SALE, now);
            if (updated == 0) {
                throw stockFailure(productId);
            }
        });
    }

    /** 取消訂單歸還庫存（B09 顧客取消、B10 管理員取消） */
    @Transactional(propagation = Propagation.MANDATORY)
    public void restore(Order order) {
        Instant now = Instant.now();
        order.getItems().stream()
                .sorted(Comparator.comparing(item -> item.getProduct().getId()))
                .forEach(item -> productRepository.increaseStock(item.getProduct().getId(), item.getQuantity(), now));
    }

    /** 扣庫存失敗時，查出原因給使用者清楚的訊息 */
    private BusinessException stockFailure(Long productId) {
        return productRepository.findById(productId)
                .filter(p -> p.getStatus() == ProductStatus.ON_SALE)
                .map(p -> new BusinessException(ErrorCode.INSUFFICIENT_STOCK,
                        "「" + p.getName() + "」庫存不足，目前剩 " + p.getStock() + " 件"))
                .orElseGet(() -> new BusinessException(ErrorCode.PRODUCT_UNAVAILABLE));
    }
}
