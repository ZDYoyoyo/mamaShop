import http from '@/api/http'

/** 後台分類 API（/api/admin/categories，限管理員）。比前台多了排序欄位 sortOrder */

export function listAdminCategories() {
  return http.get('/admin/categories')
}

/** { name, sortOrder } */
export function createCategory(payload) {
  return http.post('/admin/categories', payload)
}

export function updateCategory(id, payload) {
  return http.put(`/admin/categories/${id}`, payload)
}

/** 底下還有商品（含下架）時後端回 409 CATEGORY_HAS_PRODUCTS */
export function deleteCategory(id) {
  return http.delete(`/admin/categories/${id}`)
}
