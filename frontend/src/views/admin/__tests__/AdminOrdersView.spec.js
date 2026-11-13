import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/orders', () => ({ listAdminOrders: vi.fn() }))

import { listAdminOrders } from '@/api/admin/orders'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import AdminOrdersView from '@/views/admin/AdminOrdersView.vue'

const ORDER = {
  orderNo: 'M2611130000000001',
  status: 'PENDING_CONFIRM',
  paymentStatus: 'UNPAID',
  shippingMethod: 'CVS_PICKUP',
  customerName: '王小明',
  recipientName: '王媽媽',
  totalAmount: 1840,
  totalQuantity: 3,
  createdAt: '2026-11-13T02:30:00Z',
  version: 0,
}

async function mountAt(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(AdminOrdersView, { global: { plugins: [router] } })
  await settle()
  return { wrapper, router }
}

describe('AdminOrdersView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(listAdminOrders).mockReset()
    vi.mocked(listAdminOrders).mockResolvedValue({ items: [ORDER], page: 1, size: 20, totalElements: 1, totalPages: 1 })
  })

  it('列出訂單：時間、狀態、收件人（和下單會員不同時一起顯示）、金額，連到詳情', async () => {
    const { wrapper } = await mountAt('/admin/orders')

    const text = wrapper.find('.order').text()
    expect(text).toContain('2026/11/13 10:30')
    expect(text).toContain('待確認')
    expect(text).toContain('王媽媽')
    expect(text).toContain('（王小明 訂）')
    expect(text).toContain('NT$ 1,840')
    expect(text).toContain('超商取貨・3 件')
    expect(wrapper.find('a.order').attributes('href')).toBe('/admin/orders/M2611130000000001')
  })

  it('依網址的條件查詢；亂填的狀態當作沒填', async () => {
    await mountAt('/admin/orders?status=SHIPPED&shippingMethod=HOME_DELIVERY&page=2')
    expect(listAdminOrders).toHaveBeenLastCalledWith({ status: 'SHIPPED', shippingMethod: 'HOME_DELIVERY', page: 2, size: 20 })

    await mountAt('/admin/orders?status=HACK')
    expect(listAdminOrders).toHaveBeenLastCalledWith({ status: '', shippingMethod: '', page: 1, size: 20 })
  })

  it('點狀態標籤：寫回網址並回到第 1 頁', async () => {
    const { wrapper, router } = await mountAt('/admin/orders?page=3')

    await wrapper.findAll('.tabs button').find((b) => b.text() === '備貨中').trigger('click')
    await settle()

    expect(router.currentRoute.value.query).toEqual({ status: 'PROCESSING' })
  })

  it('輸入訂單編號：直接打開那筆訂單（自動轉大寫）', async () => {
    const { wrapper, router } = await mountAt('/admin/orders')

    await wrapper.find('.lookup input').setValue(' m2611130000000009 ')
    await wrapper.find('.lookup input').trigger('keyup', { key: 'Enter' })
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/admin/orders/M2611130000000009'))
  })

  it('篩選後沒有訂單：說明是哪個狀態', async () => {
    vi.mocked(listAdminOrders).mockResolvedValue({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 })

    const { wrapper } = await mountAt('/admin/orders?status=RETURNED')

    expect(wrapper.text()).toContain('沒有「已退回」的訂單')
  })
})
