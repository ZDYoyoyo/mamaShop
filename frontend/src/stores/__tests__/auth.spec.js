import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/auth', () => ({ login: vi.fn(), getMe: vi.fn() }))

import * as authApi from '@/api/auth'
import { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

describe('auth store', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('登入後存進 localStorage，重新整理（新的 store）後仍是登入狀態', () => {
    useAuthStore().setSession({ accessToken: 'token-1', user: { name: '媽媽', role: 'ADMIN' } })

    // 模擬重新整理頁面：建立全新的 Pinia，store 從 localStorage 讀回資料
    setActivePinia(createPinia())
    const reloaded = useAuthStore()

    expect(reloaded.isLoggedIn).toBe(true)
    expect(reloaded.isAdmin).toBe(true)
    expect(reloaded.user.name).toBe('媽媽')
  })

  it('顧客不是管理員', () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'token-1', user: { name: '王小明', role: 'USER' } })

    expect(auth.isAdmin).toBe(false)
  })

  it('登出後清除所有資料', () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'token-1', user: { name: '王小明', role: 'USER' } })

    auth.logout()

    expect(auth.isLoggedIn).toBe(false)
    expect(auth.user).toBeNull()
    expect(localStorage.getItem('mamashop.token')).toBeNull()
    expect(localStorage.getItem('mamashop.user')).toBeNull()
  })

  it('localStorage 裡的資料壞掉時不會讓網站壞掉', () => {
    localStorage.setItem('mamashop.token', 'token-1')
    localStorage.setItem('mamashop.user', '{這不是 JSON')

    const auth = useAuthStore()

    expect(auth.isLoggedIn).toBe(true)
    expect(auth.user).toBeNull()
  })

  it('login：成功時記下 token 與會員資料', async () => {
    vi.mocked(authApi.login).mockResolvedValue({ accessToken: 'tok', user: { name: '王小明', role: 'USER' } })
    const auth = useAuthStore()

    await auth.login({ email: 'a@example.com', password: 'Passw0rd!' })

    expect(auth.isLoggedIn).toBe(true)
    expect(localStorage.getItem('mamashop.token')).toBe('tok')
  })

  it('login：失敗時丟出錯誤，維持未登入', async () => {
    vi.mocked(authApi.login).mockRejectedValue(
      new ApiError({ status: 401, code: 'INVALID_CREDENTIALS', message: 'Email 或密碼錯誤' }),
    )
    const auth = useAuthStore()

    await expect(auth.login({ email: 'a@example.com', password: 'x' })).rejects.toThrow('Email 或密碼錯誤')
    expect(auth.isLoggedIn).toBe(false)
  })

  it('loadProfile：token 有效時更新會員資料', async () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'tok', user: { name: '舊名字', role: 'USER' } })
    vi.mocked(authApi.getMe).mockResolvedValue({ name: '新名字', role: 'USER' })

    await auth.loadProfile()

    expect(auth.user.name).toBe('新名字')
    expect(authApi.getMe).toHaveBeenCalledWith({ skipAuthRedirect: true })
  })

  it('loadProfile：token 已失效（401）時安靜登出', async () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'expired', user: { name: '王小明', role: 'USER' } })
    vi.mocked(authApi.getMe).mockRejectedValue(new ApiError({ status: 401, code: 'UNAUTHORIZED', message: '請先登入' }))

    await auth.loadProfile()

    expect(auth.isLoggedIn).toBe(false)
  })

  it('loadProfile：網路錯誤時保留登入狀態', async () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'tok', user: { name: '王小明', role: 'USER' } })
    vi.mocked(authApi.getMe).mockRejectedValue(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '無法連線' }))

    await auth.loadProfile()

    expect(auth.isLoggedIn).toBe(true)
  })
})
