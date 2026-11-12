import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import ProductCard from '@/components/ProductCard.vue'
import { routes } from '@/router'

function mountCard(product) {
  // 用記憶體路由（不碰瀏覽器網址），讓 RouterLink 能產生正確的 href
  const router = createRouter({ history: createMemoryHistory(), routes })
  return mount(ProductCard, { props: { product }, global: { plugins: [router] } })
}

describe('ProductCard', () => {
  it('顯示名稱、價格，並連到商品詳情', () => {
    const wrapper = mountCard({ id: 7, name: '帆布托特包', price: 890, stock: 20, imageUrl: '/uploads/a.jpg' })

    expect(wrapper.text()).toContain('帆布托特包')
    expect(wrapper.text()).toContain('NT$ 890')
    expect(wrapper.find('a').attributes('href')).toBe('/products/7')
    expect(wrapper.find('img').attributes('src')).toBe('/uploads/a.jpg')
  })

  it('售完時顯示「已售完」並淡化', () => {
    const wrapper = mountCard({ id: 1, name: '限量包', price: 500, stock: 0, imageUrl: null })

    expect(wrapper.text()).toContain('已售完')
    expect(wrapper.find('a').classes()).toContain('sold-out')
  })

  it('剩少量時提醒', () => {
    const wrapper = mountCard({ id: 1, name: '限量包', price: 500, stock: 2, imageUrl: null })

    expect(wrapper.text()).toContain('僅剩 2 件')
  })

  it('沒有圖片時顯示「暫無圖片」', () => {
    const wrapper = mountCard({ id: 1, name: '包包', price: 500, stock: 10, imageUrl: null })

    expect(wrapper.find('img').exists()).toBe(false)
    expect(wrapper.text()).toContain('暫無圖片')
  })
})
