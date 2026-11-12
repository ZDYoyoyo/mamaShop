import { beforeEach, describe, expect, it } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'
import { authGuard, safeRedirect } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'

describe('authGuard（路由守衛）', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('需要登入的頁面：未登入時導向登入頁，帶上原本的網址', () => {
    const to = { meta: { requiresAuth: true }, fullPath: '/checkout?step=2' }

    expect(authGuard(to)).toEqual({ name: 'login', query: { redirect: '/checkout?step=2' } })
  })

  it('需要登入的頁面：已登入時放行', () => {
    useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', role: 'USER' } })

    expect(authGuard({ meta: { requiresAuth: true }, fullPath: '/checkout' })).toBe(true)
  })

  it('登入頁、註冊頁：已登入時回首頁', () => {
    useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', role: 'USER' } })

    expect(authGuard({ meta: { guestOnly: true }, fullPath: '/login' })).toEqual({ name: 'home' })
  })

  it('一般頁面：都放行', () => {
    expect(authGuard({ meta: {}, fullPath: '/products' })).toBe(true)
  })

  describe('後台（requiresAdmin）', () => {
    const toAdmin = { meta: { requiresAuth: true, requiresAdmin: true }, path: '/admin', fullPath: '/admin', query: {}, hash: '' }

    it('未登入：導向登入頁，登入後回到後台', () => {
      expect(authGuard(toAdmin)).toEqual({ name: 'login', query: { redirect: '/admin' } })
    })

    it('一般會員：顯示找不到頁面，網址不變', () => {
      useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', role: 'USER' } })

      expect(authGuard(toAdmin)).toMatchObject({ name: 'not-found', params: { pathMatch: ['admin'] } })
    })

    it('管理員：放行', () => {
      useAuthStore().setSession({ accessToken: 't', user: { name: '媽媽', role: 'ADMIN' } })

      expect(authGuard(toAdmin)).toBe(true)
    })
  })
})

describe('safeRedirect（防止 open redirect）', () => {
  it('站內路徑照用', () => {
    expect(safeRedirect('/cart')).toBe('/cart')
    expect(safeRedirect('/products?categoryId=2')).toBe('/products?categoryId=2')
  })

  it('外部網址一律回首頁', () => {
    expect(safeRedirect('https://evil.example.com')).toBe('/')
    expect(safeRedirect('//evil.example.com')).toBe('/') // 瀏覽器會把 // 開頭當成另一個網站
    expect(safeRedirect('/\\evil.example.com')).toBe('/')
    expect(safeRedirect('javascript:alert(1)')).toBe('/')
  })

  it('沒有值或不是字串時回首頁', () => {
    expect(safeRedirect(undefined)).toBe('/')
    expect(safeRedirect(['/a', '/b'])).toBe('/')
  })
})
