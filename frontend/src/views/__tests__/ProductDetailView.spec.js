import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'

vi.mock('@/api/catalog', () => ({ getProduct: vi.fn() }))

import { getProduct } from '@/api/catalog'
import { ApiError } from '@/api/http'
import ProductDetailView from '@/views/ProductDetailView.vue'
import { routes } from '@/router'

async function mountDetail(id) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(`/products/${id}`)
  const wrapper = mount(ProductDetailView, { props: { id: String(id) }, global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('ProductDetailView', () => {
  // 注意要用大括號：寫成 () => vi.mocked(getProduct).mockReset() 會「回傳」mock 函式，
  // Vitest 會把 beforeEach 回傳的函式當成測試結束後的清理函式，等於每個測試結束都多呼叫一次 getProduct()
  beforeEach(() => {
    vi.mocked(getProduct).mockReset()
  })

  it('顯示商品資訊，描述保留換行且不當作 HTML', async () => {
    vi.mocked(getProduct).mockResolvedValue({
      id: 3,
      name: '帆布托特包',
      price: 890,
      stock: 3,
      imageUrl: null,
      categoryId: 1,
      categoryName: '手提包',
      description: '第一行\n<script>alert(1)</script>',
    })

    const wrapper = await mountDetail(3)

    expect(wrapper.find('h1').text()).toBe('帆布托特包')
    expect(wrapper.text()).toContain('NT$ 890')
    expect(wrapper.text()).toContain('僅剩 3 件')
    expect(wrapper.find('.category').attributes('href')).toBe('/products?categoryId=1')
    // 描述裡的 <script> 只會被當成文字顯示，不會變成真的元素
    expect(wrapper.find('.description').text()).toContain('<script>alert(1)</script>')
    expect(wrapper.find('.description script').exists()).toBe(false)
    expect(document.title).toBe('帆布托特包 | mamaShop')
  })

  it('下架或不存在的商品顯示「找不到這個商品」', async () => {
    vi.mocked(getProduct).mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', message: '找不到商品' }))

    const wrapper = await mountDetail(999)

    expect(wrapper.text()).toContain('找不到這個商品')
  })
})
