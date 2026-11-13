import http from '@/api/http'

/**
 * 後台運送方式 API（/api/admin/shipping-methods，限管理員）。
 * 種類（宅配、超商取貨）由程式定義，只能修改名稱、運費、啟用、排序，不能新增或刪除。
 */

export function listAdminShippingMethods() {
  return http.get('/admin/shipping-methods')
}

/** { name, fee, enabled, sortOrder }；停用最後一種啟用中的方式時後端回 409 LAST_SHIPPING_METHOD */
export function updateShippingMethod(id, payload) {
  return http.put(`/admin/shipping-methods/${id}`, payload)
}
