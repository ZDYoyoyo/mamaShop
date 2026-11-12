import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { nextTick } from 'vue'

vi.mock('@/api/catalog', () => ({ getProduct: vi.fn() }))

import { getProduct } from '@/api/catalog'
import { ApiError } from '@/api/http'
import { useCartStore } from '@/stores/cart'

const tote = { id: 1, name: '帆布托特包', price: 890, stock: 10, imageUrl: '/uploads/a.jpg' }
const fabric = { id: 2, name: '碎花布', price: 0.1, stock: 50, imageUrl: null }

describe('cart store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(getProduct).mockReset()
  })

  it('加入同一商品會累加數量', () => {
    const cart = useCartStore()
    cart.addItem(tote, 2)
    cart.addItem(tote, 3)

    expect(cart.items).toHaveLength(1)
    expect(cart.items[0].quantity).toBe(5)
    expect(cart.totalQuantity).toBe(5)
  })

  it('數量不超過庫存，也不超過每件上限 99', () => {
    const cart = useCartStore()
    expect(cart.addItem(tote, 8)).toBe(8)
    expect(cart.addItem(tote, 8)).toBe(10) // 庫存只有 10

    cart.addItem({ ...fabric, stock: 500 }, 150)
    expect(cart.items[1].quantity).toBe(99)
  })

  it('修改數量：限制在 1～庫存之間', () => {
    const cart = useCartStore()
    cart.addItem(tote, 1)

    cart.updateQuantity(1, 0)
    expect(cart.items[0].quantity).toBe(1)
    cart.updateQuantity(1, 100)
    expect(cart.items[0].quantity).toBe(10)
    cart.updateQuantity(1, 4)
    expect(cart.items[0].quantity).toBe(4)
  })

  it('小計沒有小數誤差（0.1 × 3 = 0.3，不是 0.30000000000000004）', () => {
    const cart = useCartStore()
    cart.addItem(fabric, 3)

    expect(cart.subtotal).toBe(0.3)
  })

  it('刪除品項', () => {
    const cart = useCartStore()
    cart.addItem(tote, 1)
    cart.addItem(fabric, 1)

    cart.removeItem(1)

    expect(cart.items.map((i) => i.productId)).toEqual([2])
  })

  it('存進 localStorage，重新整理後還在', async () => {
    useCartStore().addItem(tote, 2)
    await nextTick() // 等 watch 把資料存進 localStorage

    setActivePinia(createPinia())
    const reloaded = useCartStore()

    expect(reloaded.items[0]).toMatchObject({ productId: 1, name: '帆布托特包', quantity: 2 })
  })

  it('localStorage 資料損壞時當作空購物車', () => {
    localStorage.setItem('mamashop.cart', '{壞掉的資料')
    expect(useCartStore().items).toEqual([])

    setActivePinia(createPinia())
    localStorage.setItem('mamashop.cart', JSON.stringify([{ productId: 'x', quantity: -1 }, { productId: 3, quantity: 2 }]))
    expect(useCartStore().items).toEqual([{ productId: 3, quantity: 2 }])
  })

  describe('refresh：更新成後端的最新資料', () => {
    it('價格變動時更新並提示', async () => {
      const cart = useCartStore()
      cart.addItem(tote, 2)
      vi.mocked(getProduct).mockResolvedValue({ ...tote, price: 990 })

      const notices = await cart.refresh()

      expect(cart.items[0].price).toBe(990)
      expect(cart.subtotal).toBe(1980)
      expect(notices).toEqual(['「帆布托特包」價格已更新'])
    })

    it('庫存不足時調低數量', async () => {
      const cart = useCartStore()
      cart.addItem(tote, 8)
      vi.mocked(getProduct).mockResolvedValue({ ...tote, stock: 3 })

      const notices = await cart.refresh()

      expect(cart.items[0].quantity).toBe(3)
      expect(notices[0]).toContain('數量已調整為 3 件')
    })

    it('售完、下架的品項標記出來，不算進小計', async () => {
      const cart = useCartStore()
      cart.addItem(tote, 1)
      cart.addItem(fabric, 10)
      vi.mocked(getProduct).mockImplementation((id) =>
        id === 1
          ? Promise.resolve({ ...tote, stock: 0 })
          : Promise.reject(new ApiError({ status: 404, code: 'NOT_FOUND', message: '找不到商品' })),
      )

      const notices = await cart.refresh()

      expect(cart.items.map((i) => i.status)).toEqual(['sold_out', 'unavailable'])
      expect(cart.subtotal).toBe(0)
      expect(cart.hasProblems).toBe(true)
      expect(notices).toEqual(['「帆布托特包」已售完', '「碎花布」已下架'])
    })

    it('網路錯誤不改變狀態（避免誤判成下架）', async () => {
      const cart = useCartStore()
      cart.addItem(tote, 1)
      vi.mocked(getProduct).mockRejectedValue(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '無法連線' }))

      await cart.refresh()

      expect(cart.items[0].status).toBe('ok')
    })
  })
})
