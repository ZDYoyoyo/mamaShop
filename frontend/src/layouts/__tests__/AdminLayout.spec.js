import { beforeEach, describe, expect, it, vi } from 'vitest'
import { flushPromises, mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { routes } from '@/router'
import { useAuthStore } from '@/stores/auth'
import AdminLayout from '@/layouts/AdminLayout.vue'

async function mountLayout() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin')
  const wrapper = mount(AdminLayout, { global: { plugins: [router] } })
  await flushPromises()
  return { wrapper, router }
}

describe('AdminLayout', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', user: { name: '媽媽', role: 'ADMIN' } })
  })

  it('顯示選單、管理員名字與後台首頁內容', async () => {
    const { wrapper } = await mountLayout()

    expect(wrapper.find('.sidebar').text()).toContain('概覽')
    expect(wrapper.find('.admin-name').text()).toBe('媽媽')
    expect(wrapper.text()).toContain('歡迎回來，媽媽')
  })

  it('登出：清除登入狀態並回到登入頁', async () => {
    const { wrapper, router } = await mountLayout()

    await wrapper.findAll('button').find((b) => b.text() === '登出').trigger('click')
    await flushPromises()

    expect(useAuthStore().isLoggedIn).toBe(false)
    // 登入頁是延遲載入的，換頁要等它的程式載入完成
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
  })
})
