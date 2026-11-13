import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/dashboard', () => ({ getDashboard: vi.fn() }))

import { getDashboard } from '@/api/admin/dashboard'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { settle } from '@/test-utils'
import AdminHomeView from '@/views/admin/AdminHomeView.vue'

function dashboard(overrides = {}) {
  return {
    todayOrders: 4,
    pendingConfirm: 2,
    awaitingShipment: 0,
    awaitingPickup: 1,
    returnedRecently: 0,
    returnedDays: 30,
    lowStockCount: 2,
    lowStockThreshold: 5,
    lowStockProducts: [
      { id: 7, name: '碎花零錢包', stock: 0 },
      { id: 8, name: '帆布托特包', stock: 3 },
    ],
    ...overrides,
  }
}

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin')
  const wrapper = mount(AdminHomeView, { global: { plugins: [router] } })
  await settle()
  return wrapper
}

const tile = (wrapper, label) => wrapper.findAll('.tile').find((t) => t.find('.label').text() === label)

describe('AdminHomeView', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', user: { name: '媽媽', role: 'ADMIN' } })
    vi.mocked(getDashboard).mockReset()
  })

  it('顯示各項數量；點數字打開篩選好的訂單列表', async () => {
    vi.mocked(getDashboard).mockResolvedValue(dashboard())

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('歡迎回來，媽媽')
    expect(tile(wrapper, '待確認').find('.value').text()).toBe('2')
    expect(tile(wrapper, '待確認').attributes('href')).toBe('/admin/orders?status=PENDING_CONFIRM')
    expect(tile(wrapper, '超商未取件').attributes('href')).toBe('/admin/orders?status=DELIVERED&shippingMethod=CVS_PICKUP')
    expect(tile(wrapper, '今日訂單').find('.value').text()).toBe('4')
  })

  it('有事要處理的數字用顏色提醒；0 的不提醒；今日訂單不算待辦', async () => {
    vi.mocked(getDashboard).mockResolvedValue(dashboard())

    const wrapper = await mountView()

    expect(tile(wrapper, '待確認').classes()).toContain('todo')
    expect(tile(wrapper, '待出貨').classes()).not.toContain('todo')
    expect(tile(wrapper, '今日訂單').classes()).not.toContain('todo')
  })

  it('低庫存商品：售完標示，連到商品編輯', async () => {
    vi.mocked(getDashboard).mockResolvedValue(dashboard())

    const wrapper = await mountView()

    const items = wrapper.findAll('.low-stock li')
    expect(items[0].text()).toContain('碎花零錢包')
    expect(items[0].text()).toContain('售完')
    expect(items[1].text()).toContain('剩 3 件')
    expect(items[0].find('a').attributes('href')).toBe('/admin/products/7/edit')
  })

  it('低庫存超過列出的數量：提示到商品列表看全部', async () => {
    vi.mocked(getDashboard).mockResolvedValue(dashboard({ lowStockCount: 25 }))

    const wrapper = await mountView()

    expect(wrapper.text()).toContain('只列出最少的 2 件')
  })

  it('重新整理失敗：保留原本的數字，顯示更新失敗', async () => {
    vi.mocked(getDashboard)
      .mockResolvedValueOnce(dashboard())
      .mockRejectedValueOnce(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '無法連線到伺服器，請檢查網路' }))
    const wrapper = await mountView()

    await wrapper.findAll('button').find((b) => b.text().includes('重新整理')).trigger('click')
    await settle()

    expect(wrapper.text()).toContain('更新失敗：無法連線到伺服器')
    expect(tile(wrapper, '待確認').find('.value').text()).toBe('2')
  })
})
