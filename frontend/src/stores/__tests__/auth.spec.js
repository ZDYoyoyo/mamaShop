import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
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
})
