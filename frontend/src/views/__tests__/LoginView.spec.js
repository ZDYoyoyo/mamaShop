import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/auth', () => ({ login: vi.fn(), getMe: vi.fn(), register: vi.fn() }))

import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import { useAuthStore } from '@/stores/auth'
import LoginView from '@/views/LoginView.vue'

async function mountAt(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(LoginView, { global: { plugins: [router] } })
  return { wrapper, router }
}

async function fillAndSubmit(wrapper, email, password) {
  await wrapper.find('input[type="email"]').setValue(email)
  await wrapper.find('input[type="password"]').setValue(password)
  await wrapper.find('form').trigger('submit')
  await settle()
}

describe('LoginView', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(authApi.login).mockReset()
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok', user: { name: '王小明', role: 'USER' } })
  })

  it('登入成功後回到 redirect 指定的頁面', async () => {
    const { wrapper, router } = await mountAt('/login?redirect=/cart')

    await fillAndSubmit(wrapper, 'buyer@example.com', 'Passw0rd!')

    expect(authApi.login).toHaveBeenCalledWith({ email: 'buyer@example.com', password: 'Passw0rd!' })
    expect(useAuthStore().isLoggedIn).toBe(true)
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/cart'))
  })

  it('redirect 是外部網址時回首頁（防止 open redirect）', async () => {
    const { wrapper, router } = await mountAt('/login?redirect=//evil.example.com')

    await fillAndSubmit(wrapper, 'buyer@example.com', 'Passw0rd!')

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/'))
  })

  it('帳號或密碼錯誤時顯示訊息，停在登入頁', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Email 或密碼錯誤' }),
    )
    const { wrapper, router } = await mountAt('/login')

    await fillAndSubmit(wrapper, 'buyer@example.com', 'wrong-password')

    await vi.waitFor(() => expect(wrapper.text()).toContain('Email 或密碼錯誤'))
    expect(router.currentRoute.value.name).toBe('login')
  })

  it('沒填資料時不送出', async () => {
    const { wrapper } = await mountAt('/login')

    await wrapper.find('form').trigger('submit')
    await settle()

    // 先等驗證訊息出現（代表驗證已經跑完），再確認沒有送出
    await vi.waitFor(() => expect(wrapper.text()).toContain('請輸入 Email'))
    expect(authApi.login).not.toHaveBeenCalled()
  })
})
