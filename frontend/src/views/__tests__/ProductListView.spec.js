import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

// 把呼叫後端的函式換成假的（vi.mock 會在 import 之前生效）
vi.mock('@/api/catalog', () => ({
  getCategories: vi.fn(),
  searchProducts: vi.fn(),
}))

import { getCategories, searchProducts } from '@/api/catalog'
import ProductListView from '@/views/ProductListView.vue'
import { routes } from '@/router'

const CATEGORIES = [
  { id: 1, name: '手提包' },
  { id: 2, name: '布料' },
]

async function mountAt(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(ProductListView, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('ProductListView', () => {
  beforeEach(() => {
    setActivePinia(createPinia())
    vi.mocked(getCategories).mockResolvedValue(CATEGORIES)
    vi.mocked(searchProducts).mockResolvedValue({
      items: [{ id: 1, name: '帆布托特包', price: 890, stock: 10, imageUrl: null }],
      page: 1,
      size: 12,
      totalElements: 1,
      totalPages: 1,
    })
  })

  it('依網址的篩選條件查詢', async () => {
    await mountAt('/products?categoryId=2&keyword=帆布&page=3')

    expect(searchProducts).toHaveBeenLastCalledWith({ categoryId: 2, keyword: '帆布', page: 3, size: 12 })
  })

  it('網址亂填的值當作沒填', async () => {
    await mountAt('/products?categoryId=abc&page=-1')

    expect(searchProducts).toHaveBeenLastCalledWith({ categoryId: null, keyword: '', page: 1, size: 12 })
  })

  it('顯示分類名稱當標題，以及總件數', async () => {
    const { wrapper } = await mountAt('/products?categoryId=1')

    expect(wrapper.find('h1').text()).toBe('手提包')
    expect(wrapper.text()).toContain('共 1 件')
    expect(wrapper.text()).toContain('帆布托特包')
  })

  it('點分類：網址改成新的分類並回到第 1 頁、清除關鍵字', async () => {
    const { wrapper, router } = await mountAt('/products?keyword=包&page=2')

    const fabricButton = wrapper.findAll('button').find((b) => b.text() === '布料')
    await fabricButton.trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.query).toEqual({ categoryId: '2' })
    expect(searchProducts).toHaveBeenLastCalledWith({ categoryId: 2, keyword: '', page: 1, size: 12 })
  })

  it('沒有商品時顯示空狀態', async () => {
    vi.mocked(searchProducts).mockResolvedValue({ items: [], page: 1, size: 12, totalElements: 0, totalPages: 0 })

    const { wrapper } = await mountAt('/products?keyword=不存在的東西')

    expect(wrapper.text()).toContain('找不到符合的商品')
  })

  it('搜尋沒有結果時，可以按「看全部商品」清除條件', async () => {
    vi.mocked(searchProducts).mockResolvedValue({ items: [], page: 1, size: 12, totalElements: 0, totalPages: 0 })
    const { wrapper, router } = await mountAt('/products?keyword=不存在的東西')

    await wrapper.findAll('button').find((b) => b.text() === '看全部商品').trigger('click')
    await flushPromises()

    expect(router.currentRoute.value.fullPath).toBe('/products')
    expect(searchProducts).toHaveBeenLastCalledWith({ categoryId: null, keyword: '', page: 1, size: 12 })
  })

  it('沒有篩選條件時（全部商品就是空的），不顯示「看全部商品」', async () => {
    vi.mocked(searchProducts).mockResolvedValue({ items: [], page: 1, size: 12, totalElements: 0, totalPages: 0 })

    const { wrapper } = await mountAt('/products')

    expect(wrapper.findAll('button').some((b) => b.text() === '看全部商品')).toBe(false)
  })

  it('後端錯誤時顯示錯誤訊息', async () => {
    vi.mocked(searchProducts).mockRejectedValue(new Error('無法連線到伺服器，請檢查網路'))

    const { wrapper } = await mountAt('/products')

    expect(wrapper.text()).toContain('無法連線到伺服器')
  })

  it('載入失敗後按「重新載入」再查一次，成功就顯示商品', async () => {
    vi.mocked(searchProducts).mockClear()
    vi.mocked(searchProducts).mockRejectedValueOnce(new Error('無法連線到伺服器，請檢查網路'))
    const { wrapper } = await mountAt('/products?categoryId=1')
    expect(wrapper.text()).toContain('載入失敗')

    await wrapper.findAll('button').find((b) => b.text() === '重新載入').trigger('click')
    await flushPromises()

    expect(searchProducts).toHaveBeenCalledTimes(2)
    expect(searchProducts).toHaveBeenLastCalledWith({ categoryId: 1, keyword: '', page: 1, size: 12 })
    expect(wrapper.text()).toContain('帆布托特包')
    expect(wrapper.text()).not.toContain('載入失敗')
  })

  it('舊的請求比較晚回來時，不會蓋掉新的結果', async () => {
    let resolveSlow
    vi.mocked(searchProducts)
      .mockImplementationOnce(() => new Promise((resolve) => (resolveSlow = resolve))) // 第一次：很慢
      .mockResolvedValueOnce({ items: [{ id: 2, name: '新結果', price: 1, stock: 9, imageUrl: null }], totalElements: 1 })

    const { wrapper, router } = await mountAt('/products?categoryId=1')
    await router.push('/products?categoryId=2')
    await flushPromises()
    // 第一次的請求現在才回來
    resolveSlow({ items: [{ id: 1, name: '舊結果', price: 1, stock: 9, imageUrl: null }], totalElements: 1 })
    await flushPromises()

    expect(wrapper.text()).toContain('新結果')
    expect(wrapper.text()).not.toContain('舊結果')
  })
})
