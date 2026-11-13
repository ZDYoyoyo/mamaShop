package com.mamashop.product.cache;

/** 商品快取的名稱（@Cacheable 與清除時共用，避免打錯字） */
public final class ProductCacheNames {

    /** 商品詳情，key 是商品 id */
    public static final String DETAIL = "productDetail";

    /** 商品列表（不含關鍵字搜尋），key 是「分類:頁碼:每頁筆數」 */
    public static final String LIST = "productList";

    private ProductCacheNames() {
    }
}
