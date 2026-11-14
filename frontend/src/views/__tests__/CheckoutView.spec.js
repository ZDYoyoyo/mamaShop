import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/catalog', () => ({ getProduct: vi.fn(), getShippingMethods: vi.fn() }))
vi.mock('@/api/orders', () => ({ createOrder: vi.fn(), getOrder: vi.fn(), getCheckoutToken: vi.fn() }))
vi.mock('@/api/payments', () => ({ getPaymentOptions: vi.fn(), startPayment: vi.fn(), redirectToPayment: vi.fn() }))
vi.mock('@/api/logistics', () => ({ getLogisticsOptions: vi.fn(), getCvsMapForm: vi.fn(), redirectToCvsMap: vi.fn() }))

import { getProduct, getShippingMethods } from '@/api/catalog'
import { createOrder, getCheckoutToken } from '@/api/orders'
import { getPaymentOptions, redirectToPayment, startPayment } from '@/api/payments'
import { getCvsMapForm, getLogisticsOptions, redirectToCvsMap } from '@/api/logistics'
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

async function mountCheckout(path = '/checkout') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
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
    vi.mocked(getCheckoutToken).mockReset()
    vi.mocked(getCheckoutToken).mockResolvedValue({ token: 'token-1', expiresInSeconds: 1800 })
    vi.mocked(getPaymentOptions).mockReset()
    vi.mocked(getPaymentOptions).mockResolvedValue({ onlinePaymentEnabled: false, paymentTimeoutMinutes: 30 })
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: false })
    vi.mocked(startPayment).mockReset()
    vi.mocked(redirectToPayment).mockReset()
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
      paymentMethod: 'COD',
    }, 'token-1') // 進入結帳頁時取得的一次性識別碼（R02）
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

  it('結帳識別碼過期：自動換新的，再按一次就用新的送出（R02）', async () => {
    useCartStore().addItem(tote, 1)
    const { wrapper, router } = await mountCheckout()
    vi.mocked(getCheckoutToken).mockResolvedValue({ token: 'token-2', expiresInSeconds: 1800 })
    vi.mocked(createOrder)
      .mockRejectedValueOnce(new ApiError({ status: 409, code: 'ORDER_TOKEN_INVALID', message: '結帳頁已過期，請再按一次「確認下單」' }))
      .mockResolvedValueOnce({ orderNo: 'M2611120000000002' })
    await input(wrapper, '收件地址').setValue('台北市')

    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('結帳頁已過期'))
    expect(router.currentRoute.value.name).toBe('checkout')

    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/checkout/complete/M2611120000000002'))
    expect(vi.mocked(createOrder).mock.calls.map((call) => call[1])).toEqual(['token-1', 'token-2'])
  })

  it('其他失敗（例如庫存不足）後再送出，沿用同一個識別碼（後端已經還原）', async () => {
    useCartStore().addItem(tote, 1)
    const { wrapper } = await mountCheckout()
    vi.mocked(createOrder)
      .mockRejectedValueOnce(new ApiError({ status: 409, code: 'INSUFFICIENT_STOCK', message: '庫存不足' }))
      .mockResolvedValueOnce({ orderNo: 'M2611120000000003' })
    await input(wrapper, '收件地址').setValue('台北市')

    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('庫存不足'))
    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(createOrder).toHaveBeenCalledTimes(2))

    expect(vi.mocked(createOrder).mock.calls.map((call) => call[1])).toEqual(['token-1', 'token-1'])
    expect(getCheckoutToken).toHaveBeenCalledTimes(1)
  })
})

describe('CheckoutView 線上付款（P01）', () => {
  beforeEach(() => {
    localStorage.clear()
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', phone: '0912345678', role: 'USER' } })
    vi.mocked(getProduct).mockResolvedValue(tote)
    vi.mocked(getShippingMethods).mockResolvedValue(METHODS)
    vi.mocked(getCheckoutToken).mockResolvedValue({ token: 'token-1', expiresInSeconds: 1800 })
    vi.mocked(createOrder).mockReset()
    vi.mocked(createOrder).mockResolvedValue({ orderNo: 'M2611140000000001', paymentMethod: 'ONLINE' })
    vi.mocked(startPayment).mockReset()
    vi.mocked(redirectToPayment).mockReset()
    vi.mocked(getPaymentOptions).mockResolvedValue({ onlinePaymentEnabled: true, paymentTimeoutMinutes: 30 })
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: false })
  })

  async function chooseOnline(wrapper) {
    await wrapper.findAll('.el-radio').find((r) => r.text().includes('線上付款')).find('input').setValue(true)
    await settle()
  }

  it('後端沒開線上付款（或查詢失敗）：只顯示貨到付款', async () => {
    vi.mocked(getPaymentOptions).mockRejectedValue(new Error('network'))
    useCartStore().addItem(tote, 1)
    const { wrapper } = await mountCheckout()

    expect(wrapper.text()).toContain('貨到付款：收到商品時付款')
    expect(wrapper.text()).not.toContain('線上付款')
  })

  it('選線上付款：送出 ONLINE，接著前往付款（整頁跳到綠界）', async () => {
    const form = { action: 'https://payment-stage.ecpay.com.tw/Cashier/AioCheckOut/V5', fields: { MerchantTradeNo: 'M261114000000000101' } }
    vi.mocked(startPayment).mockResolvedValue(form)
    const cart = useCartStore()
    cart.addItem(tote, 1)
    const { wrapper } = await mountCheckout()
    await chooseOnline(wrapper)
    expect(wrapper.text()).toContain('30 分鐘內完成付款')
    expect(wrapper.find('button.submit').text()).toBe('確認下單並前往付款')
    await input(wrapper, '收件地址').setValue('台北市')

    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(redirectToPayment).toHaveBeenCalledWith(form))
    expect(vi.mocked(createOrder).mock.calls[0][0].paymentMethod).toBe('ONLINE')
    expect(startPayment).toHaveBeenCalledWith('M2611140000000001')
    expect(cart.items).toEqual([])
    // 頁面離開前顯示「正在前往」，不會閃一下空的購物車、也不能再按一次
    expect(wrapper.text()).toContain('正在前往綠界付款頁')
    expect(wrapper.find('button.submit').exists()).toBe(false)
  })

  it('訂單成立但前往付款失敗：導向訂單頁（那裡可以再付款）', async () => {
    vi.mocked(startPayment).mockRejectedValue(new ApiError({ status: 500, code: 'INTERNAL_ERROR', message: '系統錯誤' }))
    useCartStore().addItem(tote, 1)
    const { wrapper, router } = await mountCheckout()
    await chooseOnline(wrapper)
    await input(wrapper, '收件地址').setValue('台北市')

    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/orders/M2611140000000001'))
    expect(redirectToPayment).not.toHaveBeenCalled()
  })
})

describe('CheckoutView 超商電子地圖（P02）', () => {
  beforeEach(() => {
    localStorage.clear()
    sessionStorage.clear()
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', phone: '0912345678', role: 'USER' } })
    vi.mocked(getProduct).mockResolvedValue(tote)
    vi.mocked(getShippingMethods).mockResolvedValue(METHODS)
    vi.mocked(getCheckoutToken).mockResolvedValue({ token: 'token-1', expiresInSeconds: 1800 })
    vi.mocked(createOrder).mockReset()
    vi.mocked(createOrder).mockResolvedValue({ orderNo: 'M2611140000000009', paymentMethod: 'COD' })
    vi.mocked(getPaymentOptions).mockResolvedValue({ onlinePaymentEnabled: false, paymentTimeoutMinutes: 30 })
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: true })
    vi.mocked(getCvsMapForm).mockReset()
    vi.mocked(redirectToCvsMap).mockReset()
    useCartStore().addItem(tote, 1)
  })

  async function chooseCvs(wrapper) {
    await wrapper.findAll('.el-radio').find((r) => r.text().includes('超商取貨')).find('input').setValue(true)
    await settle()
  }

  async function chooseBrand(wrapper, label) {
    await wrapper.find('.el-select').trigger('click')
    await settle()
    ;[...document.querySelectorAll('.el-select-dropdown__item')].find((o) => o.textContent.includes(label)).click()
    await settle()
  }

  it('有開電子地圖：不顯示手動填寫的欄位，沒選門市不能送出', async () => {
    const { wrapper } = await mountCheckout()
    await chooseCvs(wrapper)

    expect(wrapper.text()).toContain('選擇門市')
    expect(wrapper.text()).not.toContain('門市店號')
    await chooseBrand(wrapper, '7-ELEVEN')
    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('請選擇取貨門市'))
    expect(createOrder).not.toHaveBeenCalled()
  })

  it('按「選擇門市」：先暫存表單，再整頁跳到電子地圖（貨到付款要能代收貨款的門市）', async () => {
    const mapForm = { action: 'https://logistics-stage.ecpay.com.tw/Express/map', fields: { LogisticsSubType: 'UNIMARTC2C' } }
    vi.mocked(getCvsMapForm).mockResolvedValue(mapForm)
    const { wrapper } = await mountCheckout()
    await chooseCvs(wrapper)
    await chooseBrand(wrapper, '7-ELEVEN')
    await input(wrapper, '備註（選填）').setValue('請放門市')

    await wrapper.find('.map-button').trigger('click')
    await settle()

    expect(getCvsMapForm).toHaveBeenCalledWith({ brand: 'SEVEN', collection: true, mobile: false })
    expect(redirectToCvsMap).toHaveBeenCalledWith(mapForm)
    expect(wrapper.text()).toContain('正在前往超商電子地圖')
    const draft = JSON.parse(sessionStorage.getItem('mamashop.checkoutDraft'))
    expect(draft).toMatchObject({ shippingMethod: 'CVS_PICKUP', cvsBrand: 'SEVEN', note: '請放門市', recipientName: '王小明' })
  })

  it('沒選超商就按「選擇門市」：提示先選超商', async () => {
    const { wrapper } = await mountCheckout()
    await chooseCvs(wrapper)

    await wrapper.find('.map-button').trigger('click')

    await vi.waitFor(() => expect(wrapper.text()).toContain('請選擇超商'))
    expect(getCvsMapForm).not.toHaveBeenCalled()
  })

  it('從電子地圖回來：拿回暫存的表單、填入門市、清掉網址參數，送出時帶門市', async () => {
    sessionStorage.setItem('mamashop.checkoutDraft', JSON.stringify({
      shippingMethod: 'CVS_PICKUP', recipientName: '王大明', recipientPhone: '0987654321', cvsBrand: 'SEVEN', note: '謝謝', paymentMethod: 'COD',
    }))
    const { wrapper, router } = await mountCheckout(
      '/checkout?cvsBrand=SEVEN&cvsStoreCode=131386&cvsStoreName=' + encodeURIComponent('建盛門市') +
        '&cvsStoreAddress=' + encodeURIComponent('台北市中正區'),
    )

    expect(router.currentRoute.value.fullPath).toBe('/checkout')
    expect(wrapper.text()).toContain('7-ELEVEN 建盛門市（店號 131386）')
    expect(wrapper.text()).toContain('台北市中正區')
    expect(input(wrapper, '姓名').element.value).toBe('王大明')
    expect(sessionStorage.getItem('mamashop.checkoutDraft')).toBeNull() // 拿一次就刪掉

    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(createOrder).toHaveBeenCalled())
    expect(vi.mocked(createOrder).mock.calls[0][0]).toMatchObject({
      shippingMethod: 'CVS_PICKUP', recipientName: '王大明', recipientPhone: '0987654321',
      cvsBrand: 'SEVEN', cvsStoreCode: '131386', cvsStoreName: '建盛門市', shippingAddress: null, note: '謝謝',
    })
    expect(vi.mocked(createOrder).mock.calls[0][0]).not.toHaveProperty('cvsStoreAddress')
  })

  it('換了超商品牌：清掉之前選的門市', async () => {
    const { wrapper } = await mountCheckout('/checkout?cvsBrand=SEVEN&cvsStoreCode=131386&cvsStoreName=A')
    expect(wrapper.text()).toContain('店號 131386')

    await chooseBrand(wrapper, '全家')

    expect(wrapper.text()).not.toContain('店號 131386')
    expect(wrapper.find('.map-button').text()).toBe('選擇門市')
  })

  it('地圖回傳的門市資料有誤：顯示訊息請重選', async () => {
    const { wrapper, router } = await mountCheckout('/checkout?cvsMapError=1')

    expect(router.currentRoute.value.fullPath).toBe('/checkout')
    await chooseCvs(wrapper)
    expect(wrapper.text()).toContain('門市資料有誤，請重新選擇門市')
  })
})

describe('結帳路由', () => {
  it('結帳頁與完成頁都需要登入', () => {
    const byName = Object.fromEntries(routes.map((r) => [r.name, r]))
    expect(byName.checkout.meta.requiresAuth).toBe(true)
    expect(byName['checkout-complete'].meta.requiresAuth).toBe(true)
  })
})
