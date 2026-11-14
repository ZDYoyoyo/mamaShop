package com.mamashop.product.cache;

import java.util.Collection;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * 商品資料變動時清除快取（R01）。所有會改到前台商品內容的地方都要呼叫：
 *
 * <ul>
 *   <li>後台修改商品（AdminProductService）：新增、基本資料、上下架、庫存、圖片
 *   <li>訂單的庫存異動（OrderStockService）：下單扣庫存、取消／退回歸還庫存
 *   <li>修改、刪除分類（CategoryService）：商品詳情裡有分類名稱
 * </ul>
 *
 * <p><b>列表一律全部清除</b>：一個商品的變動可能影響很多頁（排序、上下架、分類篩選），
 * 一頁一頁算哪些要清太容易出錯；家庭小店的商品變動不頻繁，全清的代價很小。
 *
 * <p><b>交易 commit 之後才清除</b>：在交易裡呼叫時，先登記、等 commit 後才真的清。
 * <ul>
 *   <li>如果在 commit 之前清：別的請求可能在這個空檔從資料庫讀到「還沒 commit 的舊資料」，又存回快取，
 *       結果快取裡一直是舊的（最多到 TTL 10 分鐘）
 *   <li>交易 rollback（例如庫存不足下單失敗）就不清，資料沒變，不需要清
 * </ul>
 *
 * <p><b>用 evictIfPresent／invalidate，不用 evict／clear</b>：Spring 的 Cache 介面規定 evict、clear
 * 「可以延後或非同步執行」，呼叫完不保證已經清掉（測試實際抓到：清完馬上查還是舊資料）；
 * evictIfPresent、invalidate 才保證呼叫結束時已經清掉。
 *
 * <p><b>清除失敗只記 log</b>：Redis 掛掉時，訂單、商品修改照樣成功（不能因為快取讓下單失敗），
 * 快取最多 TTL（10 分鐘）後自己過期。不用 Spring 內建的 transactionAware，
 * 是因為它在 commit 後清除失敗時會把例外丟給呼叫端，變成「訂單成立了但顧客看到 500」。
 */
@Component
public class ProductCacheEvictor {

    private static final Logger log = LoggerFactory.getLogger(ProductCacheEvictor.class);

    private final CacheManager cacheManager;

    public ProductCacheEvictor(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /** 一個商品變了：清除它的詳情與所有列表 */
    public void evictProduct(Long productId) {
        evictProducts(List.of(productId));
    }

    /** 多個商品變了（例如一張訂單裡的商品） */
    public void evictProducts(Collection<Long> productIds) {
        List<Long> ids = List.copyOf(productIds);
        afterCommit(() -> {
            Cache detail = cacheManager.getCache(ProductCacheNames.DETAIL);
            if (detail != null) {
                ids.forEach(detail::evictIfPresent);
            }
            clear(ProductCacheNames.LIST);
        });
    }

    /** 影響範圍很大的變動（例如分類改名）：全部清除 */
    public void evictAll() {
        afterCommit(() -> {
            clear(ProductCacheNames.DETAIL);
            clear(ProductCacheNames.LIST);
        });
    }

    private void clear(String cacheName) {
        Cache cache = cacheManager.getCache(cacheName);
        if (cache != null) {
            cache.invalidate();
        }
    }

    private void afterCommit(Runnable eviction) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    runSafely(eviction);
                }
            });
        } else {
            runSafely(eviction);
        }
    }

    private void runSafely(Runnable eviction) {
        try {
            eviction.run();
        } catch (RuntimeException e) {
            log.warn("清除商品快取失敗，快取最多 {} 後會自動過期：{}", ProductCacheConfig.TTL, e.toString());
        }
    }
}
