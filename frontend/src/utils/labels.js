/** 後端 enum 值 → 畫面上顯示的中文 */

/** 所有超商品牌的名稱（含已停止服務的，舊訂單還是要顯示） */
const CVS_BRAND_LABELS = {
  SEVEN: '7-ELEVEN',
  FAMILY: '全家',
  HILIFE: '萊爾富',
  OK: 'OK 超商',
}

/** 結帳可以選的超商。OK 超商店到店已於 2026/7/1 停止服務（P03），不再提供 */
export const CVS_BRANDS = ['SEVEN', 'FAMILY', 'HILIFE'].map((value) => ({ value, label: CVS_BRAND_LABELS[value] }))

export function cvsBrandLabel(value) {
  return CVS_BRAND_LABELS[value] ?? value
}

export const PAYMENT_METHOD_LABELS = {
  COD: '貨到付款',
  ONLINE: '線上付款（信用卡）',
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

/** 商品狀態 */
export const PRODUCT_STATUS = {
  ON_SALE: { label: '上架中', type: 'success' },
  OFF_SALE: { label: '已下架', type: 'info' },
}

/** 付款狀態 */
export const PAYMENT_STATUS_LABELS = {
  UNPAID: '未付款',
  PAID: '已付款',
  REFUNDED: '已退款',
}

/** 操作者角色（訂單狀態紀錄） */
export const ROLE_LABELS = {
  ADMIN: '管理員',
  USER: '顧客',
}
