/** 庫存小於等於這個數字時，提醒「僅剩 N 件」（數字可以和家人討論後調整，見 docs/NOTES.md） */
export const LOW_STOCK_THRESHOLD = 5

const priceFormatter = new Intl.NumberFormat('zh-TW', {
  minimumFractionDigits: 0,
  maximumFractionDigits: 2,
})

/**
 * 金額顯示：890 → "NT$ 890"、1280.5 → "NT$ 1,280.5"。
 * 後端回傳的金額是 JSON 數字（BigDecimal 序列化），只用來「顯示」；
 * 前端不做任何金額計算，總額一律以後端算的為準。
 */
export function formatPrice(value) {
  if (value === null || value === undefined || Number.isNaN(Number(value))) {
    return ''
  }
  return `NT$ ${priceFormatter.format(Number(value))}`
}

/**
 * 庫存提示：售完、剩少量時顯示標籤；庫存充足時不顯示（回傳 null）。
 *
 * @returns {{ text: string, type: 'info' | 'warning' } | null} type 對應 Element Plus el-tag 的顏色
 */
export function stockLabel(stock) {
  if (stock <= 0) {
    return { text: '已售完', type: 'info' }
  }
  if (stock <= LOW_STOCK_THRESHOLD) {
    return { text: `僅剩 ${stock} 件`, type: 'warning' }
  }
  return null
}
