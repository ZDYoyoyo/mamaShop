import http from './http'

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

/**
 * 整個頁面跳到綠界付款頁：組一個隱藏的 form、POST 出去（綠界只接受 form POST，不能用 Axios）。
 * 欄位原封不動送出：改了任何一個，檢查碼就對不上，綠界會拒絕。
 * 正式環境的 CSP 要允許 form-action 到綠界（deploy/nginx/security-headers.conf）
 */
export function redirectToPayment({ action, fields }) {
  const form = document.createElement('form')
  form.method = 'POST'
  form.action = action
  for (const [name, value] of Object.entries(fields)) {
    const input = document.createElement('input')
    input.type = 'hidden'
    input.name = name
    input.value = value
    form.appendChild(input)
  }
  document.body.appendChild(form)
  form.submit()
}
