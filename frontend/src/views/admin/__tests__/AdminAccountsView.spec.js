import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/accounts', () => ({
  listAdmins: vi.fn(),
  createAdmin: vi.fn(),
  setAdminEnabled: vi.fn(),
  resetAdminPassword: vi.fn(),
}))

import { createAdmin, listAdmins, resetAdminPassword, setAdminEnabled } from '@/api/admin/accounts'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { useAuthStore } from '@/stores/auth'
import { settle } from '@/test-utils'
import AdminAccountsView from '@/views/admin/AdminAccountsView.vue'

const MOM = { id: 1, email: 'mom@example.com', name: '媽媽', phone: '0912345678', enabled: true, createdAt: '2026-11-01T02:00:00Z', passwordChangedAt: null }
const DAD = { id: 2, email: 'dad@example.com', name: '爸爸', phone: null, enabled: true, createdAt: '2026-11-02T02:00:00Z', passwordChangedAt: null }

async function mountView() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/accounts')
  const wrapper = mount(AdminAccountsView, { global: { plugins: [router] }, attachTo: document.body })
  await settle()
  return { wrapper, router }
}

const row = (wrapper, name) => wrapper.findAll('.row').find((r) => r.find('.name').text() === name)
const dialog = () => [...document.querySelectorAll('.el-dialog')].find((d) => d.offsetParent !== null || d.closest('.el-overlay')?.style.display !== 'none')
const dialogButton = (text) => [...dialog().querySelectorAll('.el-dialog__footer button')].find((b) => b.textContent.trim() === text)

async function fill(label, value) {
  const item = [...dialog().querySelectorAll('.el-form-item')].find((i) => i.querySelector('label')?.textContent.trim() === label)
  const input = item.querySelector('input')
  input.value = value
  input.dispatchEvent(new Event('input'))
  await settle()
}

describe('AdminAccountsView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    localStorage.clear()
    setActivePinia(createPinia())
    useAuthStore().setSession({ accessToken: 't', user: { id: 1, name: '媽媽', role: 'ADMIN' } })
    for (const fn of [listAdmins, createAdmin, setAdminEnabled, resetAdminPassword]) vi.mocked(fn).mockReset()
    vi.mocked(listAdmins).mockResolvedValue([MOM, DAD])
  })

  it('列出管理員；自己標示「你」，沒有停用按鈕', async () => {
    const { wrapper } = await mountView()

    expect(row(wrapper, '媽媽').text()).toContain('你')
    expect(row(wrapper, '媽媽').findAll('button').map((b) => b.text())).toEqual(['重設密碼'])
    expect(row(wrapper, '爸爸').findAll('button').map((b) => b.text())).toEqual(['重設密碼', '停用'])
  })

  it('停用別人：確認後呼叫 API，顯示已停用與「啟用」按鈕', async () => {
    vi.mocked(setAdminEnabled).mockResolvedValue({ ...DAD, enabled: false })
    const { wrapper } = await mountView()

    await row(wrapper, '爸爸').findAll('button').find((b) => b.text() === '停用').trigger('click')
    await settle()
    ;[...document.querySelectorAll('.el-popconfirm button')].find((b) => b.textContent.trim() === '停用').click()
    await settle()

    expect(setAdminEnabled).toHaveBeenCalledWith(2, false)
    expect(row(wrapper, '爸爸').text()).toContain('已停用')
    expect(row(wrapper, '爸爸').findAll('button').map((b) => b.text())).toContain('啟用')
  })

  it('新增管理員：兩次密碼要一樣，送出後加到列表', async () => {
    vi.mocked(createAdmin).mockResolvedValue({ id: 3, email: 'sis@example.com', name: '姊姊', phone: null, enabled: true, createdAt: '2026-11-13T02:00:00Z', passwordChangedAt: null })
    const { wrapper } = await mountView()

    await wrapper.findAll('button').find((b) => b.text() === '新增管理員').trigger('click')
    await settle()
    await fill('Email（登入帳號）', ' sis@example.com ')
    await fill('姓名', '姊姊')
    await fill('初始密碼', 'Passw0rd!')
    await fill('再輸入一次密碼', 'Passw0rd?')
    dialogButton('新增').click()
    await vi.waitFor(() => expect(dialog().textContent).toContain('兩次輸入的密碼不一樣'))
    expect(createAdmin).not.toHaveBeenCalled()

    await fill('再輸入一次密碼', 'Passw0rd!')
    dialogButton('新增').click()
    await vi.waitFor(() => expect(createAdmin).toHaveBeenCalledWith({ email: 'sis@example.com', name: '姊姊', phone: null, password: 'Passw0rd!' }))
    await settle()
    expect(row(wrapper, '姊姊')).toBeTruthy()
  })

  it('新增時 Email 已註冊：顯示在 Email 欄位下方', async () => {
    vi.mocked(createAdmin).mockRejectedValue(new ApiError({ status: 409, code: 'EMAIL_ALREADY_EXISTS', message: '此 Email 已經註冊過' }))
    const { wrapper } = await mountView()

    await wrapper.findAll('button').find((b) => b.text() === '新增管理員').trigger('click')
    await settle()
    await fill('Email（登入帳號）', 'dad@example.com')
    await fill('姓名', '爸爸2')
    await fill('初始密碼', 'Passw0rd!')
    await fill('再輸入一次密碼', 'Passw0rd!')
    dialogButton('新增').click()

    await vi.waitFor(() => expect(dialog().querySelector('.el-form-item__error')?.textContent).toContain('此 Email 已經註冊過'))
  })

  it('重設別人的密碼：自己保持登入', async () => {
    vi.mocked(resetAdminPassword).mockResolvedValue({ ...DAD, passwordChangedAt: '2026-11-13T03:00:00Z' })
    const { wrapper, router } = await mountView()

    await row(wrapper, '爸爸').findAll('button').find((b) => b.text() === '重設密碼').trigger('click')
    await settle()
    await fill('新密碼', 'NewPassw0rd')
    await fill('再輸入一次新密碼', 'NewPassw0rd')
    dialogButton('重設密碼').click()

    await vi.waitFor(() => expect(resetAdminPassword).toHaveBeenCalledWith(2, 'NewPassw0rd'))
    await settle()
    expect(useAuthStore().isLoggedIn).toBe(true)
    expect(router.currentRoute.value.name).toBe('admin-accounts')
    expect(row(wrapper, '爸爸').text()).toContain('密碼重設於')
  })

  it('重設自己的密碼：提醒會登出，成功後登出並到登入頁', async () => {
    vi.mocked(resetAdminPassword).mockResolvedValue({ ...MOM, passwordChangedAt: '2026-11-13T03:00:00Z' })
    const { wrapper, router } = await mountView()

    await row(wrapper, '媽媽').find('button').trigger('click')
    await settle()
    expect(dialog().textContent).toContain('這是你自己的帳號')
    await fill('新密碼', 'NewPassw0rd')
    await fill('再輸入一次新密碼', 'NewPassw0rd')
    dialogButton('重設密碼').click()

    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('login'))
    expect(useAuthStore().isLoggedIn).toBe(false)
    expect(router.currentRoute.value.query.redirect).toBe('/admin/accounts')
  })
})
