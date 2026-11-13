import http from '@/api/http'

/** 後台訂單 API（/api/admin/orders，限管理員） */

/** 列表：status、shippingMethod 可選；新到舊 */
export function listAdminOrders({ status, shippingMethod, page = 1, size = 20 } = {}) {
  return http.get('/admin/orders', {
    params: { status: status || undefined, shippingMethod: shippingMethod || undefined, page, size },
  })
}

/** 詳情：{ order, customer, histories, version } */
export function getAdminOrder(orderNo) {
  return http.get(`/admin/orders/${encodeURIComponent(orderNo)}`)
}

/**
 * 訂單操作：action 是 confirm / ship / deliver / complete / cancel / return（對應後端的網址）。
 * body 一定要有 version（畫面上看到的版本號）；ship 要 trackingNo、return 要 restock。
 * 回傳更新後的詳情（含新的版本號）。
 */
export function runOrderAction(action, orderNo, body) {
  return http.post(`/admin/orders/${encodeURIComponent(orderNo)}/${action}`, body)
}
