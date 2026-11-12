import { computed, ref, watch } from 'vue'
import { defineStore } from 'pinia'
import { getProduct } from '@/api/catalog'

const STORAGE_KEY = 'mamashop.cart'

/** 單一商品最多買幾件（和後端 B08 的限制一致） */
export const MAX_QUANTITY_PER_PRODUCT = 99

/**
 * 購物車（Pinia store），存在 localStorage：關掉瀏覽器再打開，購物車還在。
 *
 * 每個品項：{ productId, name, price, imageUrl, stock, quantity, status }
 * - name、price、imageUrl、stock 是「加入時」的資料，打開購物車頁會呼叫 refresh() 更新成最新的
 * - status：'ok' 可購買／'sold_out' 售完／'unavailable' 下架或不存在
 *
 * 金額只用來顯示，結帳時一律以後端計算的為準（前端的資料可以被改，不能信任）。
 * 不需要登入就能使用；登出也不會清空（同一台電腦的下一位使用者會看到——家用網站可接受，見 NOTES）。
 */
export const useCartStore = defineStore('cart', () => {
  const items = ref(load())

  /** 購物車圖示上的數字：總件數 */
  const totalQuantity = computed(() => items.value.reduce((sum, item) => sum + item.quantity, 0))

  /** 可以結帳的品項（售完、下架的不算） */
  const purchasableItems = computed(() => items.value.filter((item) => item.status === 'ok'))

  /**
   * 商品小計。用「分」為單位的整數計算再換回元：
   * JavaScript 的小數運算有誤差（0.1 * 3 = 0.30000000000000004），金額要避免
   */
  const subtotal = computed(
    () => purchasableItems.value.reduce((sum, item) => sum + toCents(item.price) * item.quantity, 0) / 100,
  )

  const hasProblems = computed(() => items.value.some((item) => item.status !== 'ok'))

  /**
   * 加入購物車。同一商品再加一次會累加數量，但不超過庫存與每件上限。
   *
   * @param product 商品詳情（後端 ProductDetailResponse）
   * @returns 實際的數量（可能因為庫存被調低）
   */
  function addItem(product, quantity = 1) {
    const existing = items.value.find((item) => item.productId === product.id)
    const max = maxQuantity(product.stock)
    if (existing) {
      existing.quantity = Math.min(existing.quantity + quantity, max)
      Object.assign(existing, snapshot(product))
      return existing.quantity
    }
    const added = { productId: product.id, ...snapshot(product), quantity: Math.min(quantity, max) }
    items.value.push(added)
    return added.quantity
  }

  function updateQuantity(productId, quantity) {
    const item = items.value.find((i) => i.productId === productId)
    if (!item) return
    item.quantity = Math.max(1, Math.min(Math.floor(quantity) || 1, maxQuantity(item.stock)))
  }

  function removeItem(productId) {
    items.value = items.value.filter((item) => item.productId !== productId)
  }

  /** 清空購物車 */
  function clear() {
    items.value = []
  }

  /** 結帳成功後移除已下單的品項（售完、下架的品項沒有結帳，留在購物車讓顧客自己處理） */
  function removeItems(productIds) {
    const ordered = new Set(productIds)
    items.value = items.value.filter((item) => !ordered.has(item.productId))
  }

  /**
   * 向後端取得每個商品的最新資料（價格、名稱、圖片、庫存、是否下架）。
   * 庫存比購物車數量少時，自動把數量調低。
   *
   * @returns 有變動的品項說明（例如「XXX 已售完」），讓頁面提示使用者
   */
  async function refresh() {
    const results = await Promise.allSettled(items.value.map((item) => getProduct(item.productId)))
    const notices = []
    let unreachable = false
    results.forEach((result, index) => {
      const item = items.value[index]
      if (result.status === 'rejected') {
        // 404：下架或不存在。其他錯誤（網路問題）不改變狀態，避免誤判
        if (result.reason?.status === 404) {
          if (item.status !== 'unavailable') notices.push(`「${item.name}」已下架`)
          item.status = 'unavailable'
        } else {
          unreachable = true
        }
        return
      }
      const product = result.value
      if (Number(product.price) !== Number(item.price)) {
        notices.push(`「${product.name}」價格已更新`)
      }
      Object.assign(item, snapshot(product))
      if (product.stock <= 0) {
        notices.push(`「${product.name}」已售完`)
      } else if (item.quantity > product.stock) {
        item.quantity = product.stock
        notices.push(`「${product.name}」庫存不足，數量已調整為 ${product.stock} 件`)
      }
    })
    // 網路問題查不到：告訴顧客畫面上的價格、庫存可能不是最新的（下單時後端會再檢查）
    if (unreachable) notices.push('目前無法確認最新的價格與庫存，送出訂單時會再確認')
    return notices
  }

  // items 一有變動就存回 localStorage（deep：數量改變也算）
  watch(items, (value) => save(value), { deep: true })

  return {
    items,
    totalQuantity,
    purchasableItems,
    subtotal,
    hasProblems,
    addItem,
    updateQuantity,
    removeItem,
    clear,
    removeItems,
    refresh,
  }
})

function snapshot(product) {
  return {
    name: product.name,
    price: Number(product.price),
    imageUrl: product.imageUrl ?? null,
    stock: product.stock,
    status: product.stock > 0 ? 'ok' : 'sold_out',
  }
}

function maxQuantity(stock) {
  return Math.max(1, Math.min(stock ?? MAX_QUANTITY_PER_PRODUCT, MAX_QUANTITY_PER_PRODUCT))
}

function toCents(price) {
  return Math.round(Number(price) * 100)
}

/** 讀取購物車；資料損壞或格式不對就當作空的，不讓網站壞掉 */
function load() {
  try {
    const parsed = JSON.parse(localStorage.getItem(STORAGE_KEY) ?? '[]')
    if (!Array.isArray(parsed)) return []
    return parsed.filter(
      (item) => Number.isInteger(item?.productId) && Number.isInteger(item?.quantity) && item.quantity > 0,
    )
  } catch {
    return []
  }
}

function save(items) {
  try {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(items))
  } catch {
    // 存不了（無痕模式等）就只在這次瀏覽期間有效
  }
}
