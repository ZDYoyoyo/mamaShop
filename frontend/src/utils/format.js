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

const dateTimeFormatter = new Intl.DateTimeFormat('zh-TW', {
  timeZone: 'Asia/Taipei',
  year: 'numeric',
  month: '2-digit',
  day: '2-digit',
  hour: '2-digit',
  minute: '2-digit',
  // h23：00～23 點。用 hour12: false 時，部分瀏覽器會把午夜顯示成 24:05
  hourCycle: 'h23',
})

/**
 * 時間顯示：後端回傳 ISO 格式的 UTC 時間（Instant），轉成台灣時間 "2026/11/12 14:05"。
 * 固定用台灣時區，不管顧客的電腦設定在哪個時區。
 *
 * 用 formatToParts 取出年月日時分再自己組字串：直接 format() 時，日期和時間中間的空白
 * 會因瀏覽器版本不同而是一般空白或「窄空白」（U+2009），結果不一致。
 */
export function formatDateTime(value) {
  if (!value) return ''
  const parts = Object.fromEntries(
    dateTimeFormatter.formatToParts(new Date(value)).map((part) => [part.type, part.value]),
  )
  return `${parts.year}/${parts.month}/${parts.day} ${parts.hour}:${parts.minute}`
}
