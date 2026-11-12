import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/orders', () => ({ listMyOrders: vi.fn(), getOrder: vi.fn(), cancelOrder: vi.fn() }))

import { listMyOrders } from '@/api/orders'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import OrdersView from '@/views/OrdersView.vue'

const ORDER = {
  orderNo: 'M2611120000000001',
  status: 'PENDING_CONFIRM',
  paymentStatus: 'UNPAID',
  shippingMethod: 'CVS_PICKUP',
  totalAmount: 1840,
  firstProductName: '帆布托特包',
  totalQuantity: 3,
  createdAt: '2026-11-12T02:30:00Z',
}

async function mountAt(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(OrdersView, { global: { plugins: [router] } })
  await settle()
  return { wrapper, router }
}

describe('OrdersView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(listMyOrders).mockReset()
    vi.mocked(listMyOrders).mockResolvedValue({ items: [ORDER], page: 1, size: 10, totalElements: 1, totalPages: 1 })
  })

  it('列出訂單：時間（台灣時間）、狀態、商品摘要、金額，並連到詳情', async () => {
    const { wrapper } = await mountAt('/orders')

    const text = wrapper.text()
    expect(text).toContain('2026/11/12 10:30')
    expect(text).toContain('待確認')
    expect(text).toContain('帆布托特包 等 3 件')
    expect(text).toContain('超商取貨')
    expect(text).toContain('NT$ 1,840')
    expect(wrapper.find('a.order').attributes('href')).toBe('/orders/M2611120000000001')
  })

  it('依網址的頁碼查詢', async () => {
    await mountAt('/orders?page=3')

    expect(listMyOrders).toHaveBeenCalledWith({ page: 3, size: 10 })
  })

  it('沒有訂單時顯示提示', async () => {
    vi.mocked(listMyOrders).mockResolvedValue({ items: [], page: 1, size: 10, totalElements: 0, totalPages: 0 })

    const { wrapper } = await mountAt('/orders')

    expect(wrapper.text()).toContain('還沒有訂單')
  })

  it('我的訂單與訂單詳情都需要登入', () => {
    const byName = Object.fromEntries(routes.map((r) => [r.name, r]))
    expect(byName.orders.meta.requiresAuth).toBe(true)
    expect(byName['order-detail'].meta.requiresAuth).toBe(true)
  })
})
