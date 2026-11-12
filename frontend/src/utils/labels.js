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
