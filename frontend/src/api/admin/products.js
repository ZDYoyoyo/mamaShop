import http from '@/api/http'

/**
 * 後台商品 API（/api/admin/products，限管理員）。
 * 和前台的 catalog.js 不同：包含下架商品，回傳庫存與狀態等管理用欄位。
 */

/** 商品列表：categoryId、keyword、status（ON_SALE / OFF_SALE）可選 */
export function listAdminProducts({ categoryId, keyword, status, page = 1, size = 20 } = {}) {
  return http.get('/admin/products', {
    params: { categoryId: categoryId || undefined, keyword: keyword || undefined, status: status || undefined, page, size },
  })
}

export function getAdminProduct(id) {
  return http.get(`/admin/products/${id}`)
}

/** 新增（預設下架）：{ categoryId, name, description, price, stock } */
export function createProduct(payload) {
  return http.post('/admin/products', payload)
}

/** 修改基本資料：{ categoryId, name, description, price }（不含庫存與狀態） */
export function updateProduct(id, payload) {
  return http.put(`/admin/products/${id}`, payload)
}

/** 上架／下架：status = 'ON_SALE' | 'OFF_SALE' */
export function changeProductStatus(id, status) {
  return http.patch(`/admin/products/${id}/status`, { status })
}

/** 調整庫存：delta 正數＝進貨、負數＝減少 */
export function adjustProductStock(id, delta) {
  return http.patch(`/admin/products/${id}/stock`, { delta })
}

/**
 * 上傳／更換圖片（multipart/form-data，欄位 file）。
 * 用 FormData 送出時不要自己設定 Content-Type：瀏覽器會自動加上 boundary（分隔各欄位的字串）。
 * 上傳較慢，逾時放寬到 60 秒。
 */
export function uploadProductImage(id, blob, filename = 'image.jpg') {
  const form = new FormData()
  form.append('file', blob, filename)
  return http.post(`/admin/products/${id}/image`, form, { timeout: 60_000 })
}
