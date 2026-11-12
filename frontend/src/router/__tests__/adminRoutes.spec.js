import { beforeEach, describe, expect, it } from 'vitest'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'
import { routes } from '@/router'
import { authGuard } from '@/router/guards'
import { useAuthStore } from '@/stores/auth'

/** 用真正的路由表＋守衛換頁，確認後台的子路由也受到保護（meta 由 /admin 繼承） */
async function visit(url) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  router.beforeEach(authGuard)
  await router.push(url)
  return router.currentRoute.value
}

describe('後台路由', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
  })

  it('未登入打開 /admin：到登入頁', async () => {
    const route = await visit('/admin')

    expect(route.name).toBe('login')
    expect(route.query.redirect).toBe('/admin')
  })

  it('一般會員打開 /admin：找不到頁面，網址還是 /admin', async () => {
    useAuthStore().setSession({ accessToken: 't', user: { name: '王小明', role: 'USER' } })

    const route = await visit('/admin')

    expect(route.name).toBe('not-found')
    expect(route.path).toBe('/admin')
  })

  it('管理員打開 /admin：後台首頁，使用後台版面', async () => {
    useAuthStore().setSession({ accessToken: 't', user: { name: '媽媽', role: 'ADMIN' } })

    const route = await visit('/admin')

    expect(route.name).toBe('admin-home')
    expect(route.matched.some((r) => r.meta.layout === 'admin')).toBe(true)
  })

  it('所有 /admin 開頭的路由都要求管理員（之後新增的後台頁面也一樣）', () => {
    const admin = routes.find((r) => r.path === '/admin')
    expect(admin.meta).toMatchObject({ requiresAuth: true, requiresAdmin: true })
    // 後台頁面一律寫成 /admin 的子路由，不能寫在外面
    const outside = routes.filter((r) => r.path.startsWith('/admin') && r !== admin)
    expect(outside).toEqual([])
  })
})
