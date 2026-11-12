import http from './http'

/** 顧客訂單 API（對應後端 B08、B09），都需要登入 */

/**
 * 建立訂單。只送商品 id 與數量，價格與總額由後端計算。
 *
 * @param payload { items: [{ productId, quantity }], shippingMethod, recipientName, recipientPhone,
 *                  shippingAddress?, cvsBrand?, cvsStoreCode?, cvsStoreName?, note? }
 * @returns 訂單明細（OrderDetailResponse）
 */
export function createOrder(payload) {
  return http.post('/orders', payload)
}

/** 訂單詳情（只能看自己的；別人的回 404） */
export function getOrder(orderNo) {
  return http.get(`/orders/${encodeURIComponent(orderNo)}`)
}
