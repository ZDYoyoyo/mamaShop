/**
 * 結帳表單的暫存（P02）：按「選擇門市」會整頁跳到綠界電子地圖，回來時頁面重新載入，
 * 已經填好的收件人、備註、付款方式會不見。跳走前存起來，回來時拿出來。
 *
 * - sessionStorage：只在這個分頁、關掉就消失（含姓名電話，不要留在 localStorage）
 * - 拿一次就刪掉（take）
 * - 無痕模式等情況存取會丟例外：包起來，存不了就只是要重填
 */
const KEY = 'mamashop.checkoutDraft'
const FIELDS = ['shippingMethod', 'recipientName', 'recipientPhone', 'shippingAddress', 'cvsBrand', 'note', 'paymentMethod']

export function saveCheckoutDraft(form) {
  try {
    const draft = Object.fromEntries(FIELDS.map((name) => [name, form[name]]))
    sessionStorage.setItem(KEY, JSON.stringify(draft))
  } catch {
    // 存不了就算了
  }
}

export function takeCheckoutDraft() {
  try {
    const raw = sessionStorage.getItem(KEY)
    sessionStorage.removeItem(KEY)
    if (!raw) return null
    const draft = JSON.parse(raw)
    // 只拿認得的欄位、而且是字串（sessionStorage 的內容不保證是我們存的格式）
    return Object.fromEntries(FIELDS.filter((name) => typeof draft?.[name] === 'string').map((name) => [name, draft[name]]))
  } catch {
    return null
  }
}
