/** 後端 enum 值 → 畫面上顯示的中文 */

export const CVS_BRANDS = [
  { value: 'SEVEN', label: '7-ELEVEN' },
  { value: 'FAMILY', label: '全家' },
  { value: 'HILIFE', label: '萊爾富' },
  { value: 'OK', label: 'OK 超商' },
]

export function cvsBrandLabel(value) {
  return CVS_BRANDS.find((b) => b.value === value)?.label ?? value
}

export const PAYMENT_METHOD_LABELS = {
  COD: '貨到付款',
}

/**
 * 訂單狀態：中文與標籤顏色（Element Plus el-tag 的 type）。
 * 和後端 OrderStatus 一致（B09）。
 */
export const ORDER_STATUS = {
  PENDING_PAYMENT: { label: '待付款', type: 'warning' },
  PENDING_CONFIRM: { label: '待確認', type: 'warning' },
  PROCESSING: { label: '備貨中', type: 'primary' },
  SHIPPED: { label: '已出貨', type: 'primary' },
  DELIVERED: { label: '已送達', type: 'success' },
  COMPLETED: { label: '已完成', type: 'success' },
  CANCELLED: { label: '已取消', type: 'info' },
  RETURNED: { label: '已退回', type: 'info' },
}

export function orderStatusLabel(status) {
  return ORDER_STATUS[status]?.label ?? status
}

/** 顧客可以自己取消的狀態（和後端 OrderStatus.isCancellableByCustomer 一致） */
export function isCancellableByCustomer(status) {
  return status === 'PENDING_CONFIRM' || status === 'PENDING_PAYMENT'
}

export const SHIPPING_METHOD_LABELS = {
  HOME_DELIVERY: '宅配',
  CVS_PICKUP: '超商取貨',
}
