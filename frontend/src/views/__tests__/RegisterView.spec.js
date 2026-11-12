import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/auth', () => ({ login: vi.fn(), getMe: vi.fn(), register: vi.fn() }))

import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import RegisterView from '@/views/RegisterView.vue'

async function mountAt(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(RegisterView, { global: { plugins: [router] } })
  return { wrapper, router }
}

async function fill(wrapper, { email, password, confirm, name, phone = '' }) {
  const passwords = wrapper.findAll('input[type="password"]')
  await wrapper.find('input[type="email"]').setValue(email)
  await passwords[0].setValue(password)
  await passwords[1].setValue(confirm)
  await wrapper.find('input[autocomplete="name"]').setValue(name)
  await wrapper.find('input[type="tel"]').setValue(phone)
  await wrapper.find('form').trigger('submit')
  await settle()
}

const VALID = { email: 'new@example.com', password: 'Passw0rd!', confirm: 'Passw0rd!', name: '王小明' }

describe('RegisterView', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    vi.mocked(authApi.register).mockReset()
    vi.mocked(authApi.login).mockReset()
    vi.mocked(authApi.register).mockResolvedValue({ id: 1 })
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok', user: { name: '王小明', role: 'USER' } })
  })

  it('註冊成功後自動登入，回到 redirect 頁面', async () => {
    const { wrapper, router } = await mountAt('/register?redirect=/cart')

    await fill(wrapper, VALID)

    expect(authApi.register).toHaveBeenCalledWith(expect.objectContaining({ email: 'new@example.com', name: '王小明' }))
    expect(authApi.login).toHaveBeenCalledWith({ email: 'new@example.com', password: 'Passw0rd!' })
    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/cart'))
  })

  it('兩次密碼不一樣時不送出', async () => {
    const { wrapper } = await mountAt('/register')

    await fill(wrapper, { ...VALID, confirm: 'Different1!' })

    await vi.waitFor(() => expect(wrapper.text()).toContain('兩次輸入的密碼不一樣'))
    expect(authApi.register).not.toHaveBeenCalled()
  })

  it('密碼、手機格式不符時不送出（和後端規則一致）', async () => {
    const { wrapper } = await mountAt('/register')

    await fill(wrapper, { ...VALID, password: 'short', confirm: 'short', phone: '12345' })

    await vi.waitFor(() => expect(wrapper.text()).toContain('需為 8 到 72 個英文字母、數字或符號'))
    expect(authApi.register).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(wrapper.text()).toContain('需為 09 開頭的 10 碼手機號碼'))
  })

  it('Email 已註冊時，錯誤顯示在 Email 欄位下方', async () => {
    vi.mocked(authApi.register).mockRejectedValue(
      new ApiError({ status: 409, code: 'EMAIL_ALREADY_EXISTS', message: '此 Email 已經註冊過' }),
    )
    const { wrapper, router } = await mountAt('/register')

    await fill(wrapper, VALID)

    await vi.waitFor(() => {
      const emailItem = wrapper.findAll('.el-form-item').find((item) => item.text().includes('Email'))
      expect(emailItem.text()).toContain('此 Email 已經註冊過')
    })
    expect(authApi.login).not.toHaveBeenCalled()
    expect(router.currentRoute.value.name).toBe('register')
  })
})
