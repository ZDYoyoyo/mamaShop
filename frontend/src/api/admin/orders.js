import http from '@/api/http'
import { submitPostForm } from '@/utils/postForm'

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

// ===== 綠界物流單（P03） =====

/** 這張訂單的物流單（還沒建立時是 null） */
export function getShipment(orderNo) {
  return http.get(`/admin/orders/${encodeURIComponent(orderNo)}/shipment`)
}

/** 建立物流單並出貨：{ version, note? }，回傳更新後的訂單詳情（已出貨） */
export function createShipment(orderNo, body) {
  return http.post(`/admin/orders/${encodeURIComponent(orderNo)}/shipment`, body)
}

/**
 * 列印寄件單：向後端拿到簽好的表單，在新分頁 POST 到綠界。
 * 新分頁要在「按下按鈕的當下」就打開（還沒 await 之前）：等 API 回來才開的話，瀏覽器會當成彈出視窗擋掉
 */
export async function openShipmentLabel(orderNo) {
  const tab = window.open('about:blank', LABEL_WINDOW)
  try {
    const form = await http.get(`/admin/orders/${encodeURIComponent(orderNo)}/shipment/label`)
    submitPostForm(form, { target: LABEL_WINDOW })
  } catch (e) {
    tab?.close()
    throw e
  }
}

const LABEL_WINDOW = 'mamashop-shipment-label'
