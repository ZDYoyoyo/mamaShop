import http from './http'
import { submitPostForm } from '@/utils/postForm'

/** 線上付款 API（P01，綠界 ECPay） */

/** 付款設定：{ onlinePaymentEnabled, paymentTimeoutMinutes }（不用登入） */
export function getPaymentOptions() {
  return http.get('/payments/options')
}

/**
 * 前往付款：後端建立一筆付款、算好檢查碼，回傳 { action, fields }。
 * 只能付自己的、待付款的訂單
 */
export function startPayment(orderNo) {
  return http.post(`/orders/${encodeURIComponent(orderNo)}/payment`)
}

/** 整頁跳到綠界付款頁（後端回傳的 { action, fields }） */
export function redirectToPayment(form) {
  submitPostForm(form)
}
