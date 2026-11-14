import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/orders', () => ({ listMyOrders: vi.fn(), getOrder: vi.fn(), cancelOrder: vi.fn() }))
vi.mock('@/api/payments', () => ({ getPaymentOptions: vi.fn(), startPayment: vi.fn(), redirectToPayment: vi.fn() }))

import { cancelOrder, getOrder } from '@/api/orders'
import { getPaymentOptions, redirectToPayment, startPayment } from '@/api/payments'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import OrderDetailView from '@/views/OrderDetailView.vue'

function order(overrides = {}) {
  return {
    orderNo: 'M1',
    status: 'PENDING_CONFIRM',
    paymentMethod: 'COD',
    paymentStatus: 'UNPAID',
    shippingMethod: 'HOME_DELIVERY',
    recipientName: '王小明',
    recipientPhone: '0912345678',
    shippingAddress: '台北市中正區',
    itemsAmount: 1780,
    shippingFee: 100,
    totalAmount: 1880,
    items: [{ productId: 1, productName: '帆布托特包', unitPrice: 890, quantity: 2, subtotal: 1780 }],
    createdAt: '2026-11-12T02:00:00Z',
    confirmedAt: null,
    shippedAt: null,
    deliveredAt: null,
    completedAt: null,
    cancelledAt: null,
    returnedAt: null,
    trackingNo: null,
    ...overrides,
  }
}

async function mountDetail(path = '/orders/M1') {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(path)
  const wrapper = mount(OrderDetailView, {
    props: { orderNo: 'M1' },
    global: { plugins: [router] },
    attachTo: document.body, // el-popconfirm 會把確認框放到 body 下
  })
  await settle()
  return wrapper
}

/** 按「取消訂單」→ 在跳出的確認框按「取消訂單」 */
async function confirmCancel(wrapper) {
  await wrapper.find('.actions button').trigger('click')
  await settle()
  const confirmButton = [...document.querySelectorAll('.el-popconfirm button')].find((b) => b.textContent.includes('取消訂單'))
  confirmButton.click()
  await settle()
}

describe('OrderDetailView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    vi.mocked(getOrder).mockReset()
    vi.mocked(cancelOrder).mockReset()
  })

  it('顯示商品、金額、收件資訊與進度（待確認：完成 1 步）', async () => {
    vi.mocked(getOrder).mockResolvedValue(order())

    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('帆布托特包')
    expect(wrapper.text()).toContain('NT$ 1,880')
    expect(wrapper.text()).toContain('宅配：台北市中正區')
    expect(wrapper.findAll('.el-step__head.is-success')).toHaveLength(1)
  })

  it('已出貨：顯示物流單號，進度完成 3 步，不能取消', async () => {
    vi.mocked(getOrder).mockResolvedValue(
      order({ status: 'SHIPPED', confirmedAt: '2026-11-12T03:00:00Z', shippedAt: '2026-11-12T05:00:00Z', trackingNo: 'T123' }),
    )

    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('物流單號：T123')
    expect(wrapper.findAll('.el-step__head.is-success')).toHaveLength(3)
    expect(wrapper.find('.actions').exists()).toBe(false)
  })

  it('已取消：不顯示進度，顯示取消時間', async () => {
    vi.mocked(getOrder).mockResolvedValue(order({ status: 'CANCELLED', cancelledAt: '2026-11-12T04:00:00Z' }))

    const wrapper = await mountDetail()

    expect(wrapper.find('.el-steps').exists()).toBe(false)
    expect(wrapper.text()).toContain('訂單已取消')
    expect(wrapper.text()).toContain('2026/11/12 12:00')
  })

  it('待確認時可以取消：確認後呼叫 API 並顯示新狀態', async () => {
    vi.mocked(getOrder).mockResolvedValue(order())
    vi.mocked(cancelOrder).mockResolvedValue(order({ status: 'CANCELLED', cancelledAt: '2026-11-12T04:00:00Z' }))
    const wrapper = await mountDetail()

    await confirmCancel(wrapper)

    expect(cancelOrder).toHaveBeenCalledWith('M1')
    await vi.waitFor(() => expect(wrapper.text()).toContain('訂單已取消'))
    expect(wrapper.find('.actions').exists()).toBe(false)
  })

  it('取消失敗（賣家剛好確認了）：顯示原因並重新讀取最新狀態', async () => {
    vi.mocked(getOrder)
      .mockResolvedValueOnce(order())
      .mockResolvedValueOnce(order({ status: 'PROCESSING', confirmedAt: '2026-11-12T03:00:00Z' }))
    vi.mocked(cancelOrder).mockRejectedValue(
      new ApiError({ status: 409, code: 'ORDER_CANNOT_CANCEL', message: '訂單已確認，無法自行取消，請聯絡賣家' }),
    )
    const wrapper = await mountDetail()

    await confirmCancel(wrapper)

    await vi.waitFor(() => expect(wrapper.findAll('.el-step__head.is-success')).toHaveLength(2))
    expect(getOrder).toHaveBeenCalledTimes(2)
    expect(wrapper.find('.actions').exists()).toBe(false)
  })

  it('別人的或不存在的訂單顯示「找不到這筆訂單」', async () => {
    vi.mocked(getOrder).mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', message: '找不到訂單' }))

    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('找不到這筆訂單')
  })
})

describe('OrderDetailView 線上付款（P01）', () => {
  const awaiting = (overrides = {}) =>
    order({ status: 'PENDING_PAYMENT', paymentMethod: 'ONLINE', createdAt: '2026-11-14T02:00:00Z', ...overrides })

  beforeEach(() => {
    document.body.innerHTML = ''
    vi.mocked(getOrder).mockReset()
    vi.mocked(startPayment).mockReset()
    vi.mocked(redirectToPayment).mockReset()
    vi.mocked(getPaymentOptions).mockClear()
    vi.mocked(getPaymentOptions).mockResolvedValue({ onlinePaymentEnabled: true, paymentTimeoutMinutes: 30 })
  })

  it('待付款：顯示付款期限（下單時間＋30 分鐘）與「前往付款」', async () => {
    vi.mocked(getOrder).mockResolvedValue(awaiting())
    const form = { action: 'https://payment-stage.ecpay.com.tw/x', fields: { a: '1' } }
    vi.mocked(startPayment).mockResolvedValue(form)
    const wrapper = await mountDetail()

    expect(wrapper.find('.payment-box').text()).toContain('2026/11/14 10:30') // 台灣時間 10:00 下單
    await wrapper.find('button.pay').trigger('click')
    await settle()

    expect(startPayment).toHaveBeenCalledWith('M1')
    expect(redirectToPayment).toHaveBeenCalledWith(form)
  })

  it('前往付款失敗（例如超過期限）：顯示原因並重新讀取', async () => {
    vi.mocked(getOrder).mockResolvedValueOnce(awaiting()).mockResolvedValueOnce(awaiting({ status: 'CANCELLED' }))
    vi.mocked(startPayment).mockRejectedValue(
      new ApiError({ status: 409, code: 'PAYMENT_EXPIRED', message: '已超過付款期限' }),
    )
    const wrapper = await mountDetail()

    await wrapper.find('button.pay').trigger('click')
    await settle()

    expect(document.body.textContent).toContain('已超過付款期限')
    expect(wrapper.find('.payment-box').exists()).toBe(false)
  })

  it('從綠界回來還是待付款：每 3 秒重新讀取，付款成功後顯示新狀態', async () => {
    vi.useFakeTimers({ shouldAdvanceTime: true })
    try {
      vi.mocked(getOrder)
        .mockResolvedValueOnce(awaiting())
        .mockResolvedValueOnce(awaiting())
        .mockResolvedValueOnce(awaiting({ status: 'PROCESSING', paymentStatus: 'PAID', confirmedAt: '2026-11-14T02:05:00Z' }))
      const wrapper = await mountDetail('/orders/M1?from=payment')
      expect(wrapper.text()).toContain('正在確認付款結果')

      await vi.advanceTimersByTimeAsync(3000)
      await settle()
      expect(wrapper.text()).toContain('正在確認付款結果')
      await vi.advanceTimersByTimeAsync(3000)
      await settle()

      expect(getOrder).toHaveBeenCalledTimes(3)
      expect(wrapper.find('.payment-box').exists()).toBe(false)
      expect(wrapper.text()).toContain('線上付款（信用卡）（已付款）')
    } finally {
      vi.useRealTimers()
    }
  })

  it('貨到付款的訂單不顯示付款區塊', async () => {
    vi.mocked(getOrder).mockResolvedValue(order())
    const wrapper = await mountDetail()

    expect(wrapper.find('.payment-box').exists()).toBe(false)
    expect(getPaymentOptions).not.toHaveBeenCalled()
  })
})
