import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/catalog', () => ({ getProduct: vi.fn() }))

import { getProduct } from '@/api/catalog'
import { useCartStore } from '@/stores/cart'
import CartView from '@/views/CartView.vue'
import { routes } from '@/router'

const tote = { id: 1, name: '帆布托特包', price: 890, stock: 10, imageUrl: null }
const pouch = { id: 2, name: '碎花零錢包', price: 320, stock: 5, imageUrl: null }

async function mountCart() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  const wrapper = mount(CartView, { global: { plugins: [router] } })
  await flushPromises()
  return wrapper
}

describe('CartView', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(getProduct).mockImplementation((id) => Promise.resolve(id === 1 ? tote : pouch))
  })

  it('購物車是空的時候顯示提示', async () => {
    const wrapper = await mountCart()

    expect(wrapper.text()).toContain('購物車是空的')
    expect(getProduct).not.toHaveBeenCalled()
  })

  it('列出品項與小計，進入頁面時更新商品資料', async () => {
    const cart = useCartStore()
    cart.addItem(tote, 2)
    cart.addItem(pouch, 1)

    const wrapper = await mountCart()

    expect(getProduct).toHaveBeenCalledTimes(2)
    expect(wrapper.text()).toContain('帆布托特包')
    expect(wrapper.text()).toContain('NT$ 1,780') // 890 × 2
    expect(wrapper.find('.subtotal').text()).toBe('NT$ 2,100')
  })

  it('刪除品項後小計跟著更新', async () => {
    const cart = useCartStore()
    cart.addItem(tote, 2)
    cart.addItem(pouch, 1)
    const wrapper = await mountCart()

    await wrapper.findAll('.item')[0].find('button.el-button--danger').trigger('click')

    expect(wrapper.findAll('.item')).toHaveLength(1)
    expect(wrapper.find('.subtotal').text()).toBe('NT$ 320')
  })

  it('商品售完時顯示提示與標籤，不計入小計', async () => {
    useCartStore().addItem(tote, 2)
    vi.mocked(getProduct).mockResolvedValue({ ...tote, stock: 0 })

    const wrapper = await mountCart()

    expect(wrapper.text()).toContain('「帆布托特包」已售完')
    expect(wrapper.find('.item .el-tag').text()).toBe('已售完')
    expect(wrapper.find('.subtotal').text()).toBe('NT$ 0')
  })
})
