/**
 * 後台訂單操作的定義：每個狀態可以做哪些操作、按鈕文字、確認時的說明。
 *
 * 可以做哪些操作和後端一致（B10 的 OrderStatus.canTransitionTo，加上 AdminOrderService 的限制）：
 *   待確認         → 確認、取消
 *   待付款         → 只能取消。線上付款（P01）的訂單要等金流通知付款成功才會自動進入備貨，
 *                    不能手動確認（否則等於沒收到錢就出貨；後端也會擋）
 *   備貨中         → 出貨、取消
 *   已出貨         → 已送達、退回
 *   已送達         → 完成、退回
 *   已完成／已取消／已退回 → 沒有操作
 * 前端只是決定「顯示哪些按鈕」，真正的規則在後端：狀態不允許時後端回 409。
 */
export const ORDER_ACTIONS = {
  confirm: {
    label: '確認訂單',
    type: 'primary',
    description: '確認後訂單進入「備貨中」，顧客就不能自己取消了。',
    success: '訂單已確認',
  },
  ship: {
    label: '出貨',
    type: 'primary',
    description: '填寫物流單號後訂單變成「已出貨」，顧客可以在訂單頁看到物流單號。',
    needsTrackingNo: true,
    success: '已出貨',
  },
  deliver: {
    label: '已送達',
    type: 'primary',
    description: '包裹已送到（宅配已簽收，或已到超商門市）。',
    success: '已標記為送達',
  },
  complete: {
    label: '完成（已收款）',
    type: 'success',
    description: '顧客已取貨並付款。訂單會標記為「已付款」並結束，之後不能再修改。',
    success: '訂單已完成',
  },
  cancel: {
    label: '取消訂單',
    type: 'danger',
    description: '取消後商品數量會加回庫存。取消無法復原。',
    success: '訂單已取消',
  },
  return: {
    label: '退回',
    type: 'danger',
    description: '包裹被退回（例如超商未取件、顧客拒收）。退回無法復原。',
    needsRestock: true,
    success: '已標記為退回',
  },
}

const ACTIONS_BY_STATUS = {
  PENDING_PAYMENT: ['cancel'],
  PENDING_CONFIRM: ['confirm', 'cancel'],
  PROCESSING: ['ship', 'cancel'],
  SHIPPED: ['deliver', 'return'],
  DELIVERED: ['complete', 'return'],
}

/** 這個狀態可以做的操作（key 陣列，主要操作在前） */
export function availableActions(status) {
  return ACTIONS_BY_STATUS[status] ?? []
}
