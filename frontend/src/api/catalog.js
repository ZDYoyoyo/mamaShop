import http from './http'

/**
 * 前台商品相關 API（對應後端 B05）。
 * 頁面只呼叫這些函式，不直接寫網址：後端路徑改了只要改這裡。
 */

/** 分類列表 */
export function getCategories() {
  return http.get('/categories')
}

/**
 * 商品列表（只有上架中的商品）。
 * 值是 undefined 的參數 axios 不會送出，所以沒選分類、沒填關鍵字時不用特別處理。
 *
 * @returns {Promise<{items: Array, page: number, size: number, totalElements: number, totalPages: number}>}
 */
export function searchProducts({ categoryId, keyword, page = 1, size = 12 } = {}) {
  return http.get('/products', {
    params: { categoryId, keyword: keyword || undefined, page, size },
  })
}

/** 商品詳情（下架或不存在會丟出 status 404 的 ApiError） */
export function getProduct(id) {
  return http.get(`/products/${id}`)
}

/** 可用的運送方式與運費（結帳頁用）：[{ code, name, fee }] */
export function getShippingMethods() {
  return http.get('/shipping-methods')
}
