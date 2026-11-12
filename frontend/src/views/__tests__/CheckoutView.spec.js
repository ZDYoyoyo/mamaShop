import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/catalog', () => ({ getProduct: vi.fn(), getShippingMethods: vi.fn() }))
vi.mock('@/api/orders', () => ({ createOrder: vi.fn(), getOrder: vi.fn() }))

import { getProduct, getShippingMethods } from '@/api/catalog'
import { createOrder } from '@/api/orders'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { useCartStore } from '@/stores/cart'
import { settle } from '@/test-utils'
import CheckoutView from '@/views/CheckoutView.vue'

const tote = { id: 1, name: '帆布托特包', price: 890, stock: 10, imageUrl: null }
const pouch = { id: 2, name: '碎花零錢包', price: 320, stock: 5, imageUrl: null }
const METHODS = [
  { code: 'HOME_DELIVERY', name: '宅配', fee: 100 },
  { code: 'CVS_PICKUP', name: '超商取貨', fee: 60 },
]

async function mountCheckout() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/checkout')
  const wrapper = mount(CheckoutView, { global: { plugins: [router] }, attachTo: document.body })
  await settle()
  return { wrapper, router }
}

function input(wrapper, label) {
  const item = wrapper.findAll('.el-form-item').find((i) => i.find('label').exists() && i.find('label').text() === label)
  return item.find('input, textarea')
}

describe('CheckoutView', () => {
  beforeEach(() => {
    localStorage.clear()
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', phone: '0912345678', role: 'USER' } })
    vi.mocked(getProduct).mockImplementation((id) => Promise.resolve(id === 1 ? tote : pouch))
    vi.mocked(getShippingMethods).mockResolvedValue(METHODS)
    vi.mocked(createOrder).mockReset()
    vi.mocked(createOrder).mockResolvedValue({ orderNo: 'M2611120000000001' })
  })

  it('購物車是空的時候回購物車頁', async () => {
    const { router } = await mountCheckout()

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('cart'))
  })

  it('顯示商品、預設帶入會員資料、預估總額（含運費）', async () => {
    const cart = useCartStore()
    cart.addItem(tote, 2)

    const { wrapper } = await mountCheckout()

    expect(wrapper.text()).toContain('帆布托特包 × 2')
    expect(input(wrapper, '姓名').element.value).toBe('王小明')
    expect(input(wrapper, '手機').element.value).toBe('0912345678')
    // 預設第一個運送方式（宅配 100）：890 × 2 + 100
    expect(wrapper.find('.row.total').text()).toContain('NT$ 1,880')
  })

  it('宅配：送出商品 id、數量與地址，不送超商欄位；成功後移除已下單品項並導向完成頁', async () => {
    const cart = useCartStore()
    cart.addItem(tote, 2)
    cart.addItem(pouch, 1)
    const { wrapper, router } = await mountCheckout()

    await input(wrapper, '收件地址').setValue('台北市中正區')
    await wrapper.find('form').trigger('submit')
    await settle()

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/checkout/complete/M2611120000000001'))
    expect(createOrder).toHaveBeenCalledWith({
      items: [
        { productId: 1, quantity: 2 },
        { productId: 2, quantity: 1 },
      ],
      shippingMethod: 'HOME_DELIVERY',
      recipientName: '王小明',
      recipientPhone: '0912345678',
      shippingAddress: '台北市中正區',
      cvsBrand: null,
      cvsStoreCode: null,
      cvsStoreName: null,
      note: null,
    })
    expect(cart.items).toEqual([])
  })

  it('宅配沒填地址時不送出', async () => {
    useCartStore().addItem(tote, 1)
    const { wrapper } = await mountCheckout()

    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('宅配需填寫收件地址'))
    expect(createOrder).not.toHaveBeenCalled()
  })

  it('超商取貨：顯示門市欄位，沒填門市不送出', async () => {
    useCartStore().addItem(tote, 1)
    const { wrapper } = await mountCheckout()

    await wrapper.findAll('.el-radio').find((r) => r.text().includes('超商取貨')).find('input').setValue(true)
    await settle()

    expect(wrapper.text()).toContain('門市店號')
    expect(wrapper.text()).not.toContain('收件地址')
    expect(wrapper.find('.row.total').text()).toContain('NT$ 950') // 運費變成 60

    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('請填寫門市店號'))
    expect(createOrder).not.toHaveBeenCalled()
  })

  it('連點兩下只送出一次', async () => {
    useCartStore().addItem(tote, 1)
    let resolveOrder
    vi.mocked(createOrder).mockImplementation(() => new Promise((resolve) => (resolveOrder = resolve)))
    const { wrapper } = await mountCheckout()
    await input(wrapper, '收件地址').setValue('台北市')

    await wrapper.find('form').trigger('submit')
    await settle()
    await wrapper.find('form').trigger('submit')
    await settle()

    expect(createOrder).toHaveBeenCalledTimes(1)
    expect(wrapper.find('button.submit').attributes('disabled')).toBeDefined()
    resolveOrder({ orderNo: 'M1' })
  })

  it('下單瞬間庫存不足：顯示訊息、更新購物車，不清空購物車', async () => {
    const cart = useCartStore()
    cart.addItem(tote, 2)
    const { wrapper, router } = await mountCheckout()
    vi.mocked(createOrder).mockRejectedValue(
      new ApiError({ status: 409, code: 'INSUFFICIENT_STOCK', message: '「帆布托特包」庫存不足，目前剩 1 件' }),
    )
    vi.mocked(getProduct).mockResolvedValue({ ...tote, stock: 1 })
    await input(wrapper, '收件地址').setValue('台北市')

    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('庫存不足，目前剩 1 件。購物車已更新'))
    expect(cart.items[0].quantity).toBe(1)
    expect(router.currentRoute.value.name).toBe('checkout')
  })
})

describe('結帳路由', () => {
  it('結帳頁與完成頁都需要登入', () => {
    const byName = Object.fromEntries(routes.map((r) => [r.name, r]))
    expect(byName.checkout.meta.requiresAuth).toBe(true)
    expect(byName['checkout-complete'].meta.requiresAuth).toBe(true)
  })
})
