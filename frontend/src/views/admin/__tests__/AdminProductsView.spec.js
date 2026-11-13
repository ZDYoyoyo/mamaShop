import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/products', () => ({
  listAdminProducts: vi.fn(),
  changeProductStatus: vi.fn(),
  adjustProductStock: vi.fn(),
}))
vi.mock('@/api/catalog', () => ({ getCategories: vi.fn() }))

import { changeProductStatus, listAdminProducts } from '@/api/admin/products'
import { getCategories } from '@/api/catalog'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import AdminProductsView from '@/views/admin/AdminProductsView.vue'

function product(overrides = {}) {
  return {
    id: 1,
    categoryId: 1,
    categoryName: '手提包',
    name: '帆布托特包',
    price: 890,
    stock: 12,
    imageUrl: null,
    status: 'ON_SALE',
    ...overrides,
  }
}

async function mountAt(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(AdminProductsView, { global: { plugins: [router] }, attachTo: document.body })
  await settle()
  return { wrapper, router }
}

describe('AdminProductsView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    vi.mocked(getCategories).mockResolvedValue([{ id: 1, name: '手提包' }])
    vi.mocked(listAdminProducts).mockReset()
    vi.mocked(changeProductStatus).mockReset()
    vi.mocked(listAdminProducts).mockResolvedValue({
      items: [product(), product({ id: 2, name: '碎花零錢包', stock: 0, status: 'OFF_SALE' })],
      page: 1,
      size: 20,
      totalElements: 2,
      totalPages: 1,
    })
  })

  it('列出商品：分類、價格、庫存（售完標示）、上架狀態', async () => {
    const { wrapper } = await mountAt('/admin/products')

    const rows = wrapper.findAll('.product')
    expect(rows).toHaveLength(2)
    expect(rows[0].text()).toContain('手提包・NT$ 890')
    expect(rows[0].text()).toContain('庫存 12 件')
    expect(rows[1].text()).toContain('庫存 0 件（售完）')
    expect(rows[0].find('.el-switch').classes()).toContain('is-checked')
    expect(rows[1].find('.el-switch').classes()).not.toContain('is-checked')
  })

  it('依網址的篩選條件查詢（含下架商品）', async () => {
    await mountAt('/admin/products?keyword=包&categoryId=1&status=OFF_SALE&page=2')

    expect(listAdminProducts).toHaveBeenLastCalledWith({ keyword: '包', categoryId: 1, status: 'OFF_SALE', page: 2, size: 20 })
  })

  it('下架：呼叫 API 並更新該列', async () => {
    vi.mocked(changeProductStatus).mockResolvedValue(product({ status: 'OFF_SALE' }))
    const { wrapper } = await mountAt('/admin/products')

    await wrapper.findAll('.product')[0].find('.el-switch').trigger('click')
    await settle()

    expect(changeProductStatus).toHaveBeenCalledWith(1, 'OFF_SALE')
    expect(wrapper.findAll('.product')[0].find('.el-switch').classes()).not.toContain('is-checked')
  })

  it('上下架失敗：開關維持原狀', async () => {
    vi.mocked(changeProductStatus).mockRejectedValue(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '無法連線' }))
    const { wrapper } = await mountAt('/admin/products')

    await wrapper.findAll('.product')[1].find('.el-switch').trigger('click')
    await settle()

    expect(changeProductStatus).toHaveBeenCalledWith(2, 'ON_SALE')
    expect(wrapper.findAll('.product')[1].find('.el-switch').classes()).not.toContain('is-checked')
  })

  it('沒有商品：有篩選條件時可以清除篩選', async () => {
    vi.mocked(listAdminProducts).mockResolvedValue({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 })
    const { wrapper, router } = await mountAt('/admin/products?status=OFF_SALE')

    expect(wrapper.text()).toContain('沒有符合條件的商品')
    await wrapper.findAll('button').find((b) => b.text() === '清除篩選').trigger('click')
    await settle()

    expect(router.currentRoute.value.query).toEqual({})
  })

  it('完全沒有商品：引導新增', async () => {
    vi.mocked(listAdminProducts).mockResolvedValue({ items: [], page: 1, size: 20, totalElements: 0, totalPages: 0 })

    const { wrapper } = await mountAt('/admin/products')

    expect(wrapper.text()).toContain('新增第一個商品')
  })
})
