import { beforeEach, describe, expect, it, vi } from 'vitest'
import { createPinia, setActivePinia } from 'pinia'

// 把 router 換成假的，只記錄有沒有被導向登入頁
const routerMock = vi.hoisted(() => ({
  currentRoute: { value: { name: 'orders', fullPath: '/orders?page=2' } },
  push: vi.fn(),
}))
vi.mock('@/router', () => ({ default: routerMock }))
const messageMock = vi.hoisted(() => ({ warning: vi.fn() }))
vi.mock('element-plus', () => ({ ElMessage: messageMock }))

import http, { ApiError } from '@/api/http'
import { useAuthStore } from '@/stores/auth'

/**
 * 用自訂的 adapter 取代真正的網路請求：記錄送出的 config，回傳指定的回應。
 * （adapter 是 axios 真正發出 HTTP 請求的那一層，換掉它就不需要後端）
 */
function respondWith(status, body) {
  const sent = {}
  http.defaults.adapter = async (config) => {
    sent.config = config
    const response = { data: body, status, statusText: '', headers: {}, config }
    if (status >= 400) {
      const error = new Error(`Request failed with status code ${status}`)
      error.response = response
      error.config = config
      throw error
    }
    return response
  }
  return sent
}

describe('http（axios 攔截器）', () => {
  beforeEach(() => {
    localStorage.clear()
    setActivePinia(createPinia())
    routerMock.push.mockClear()
    messageMock.warning.mockClear()
  })

  it('成功時直接回傳 data', async () => {
    respondWith(200, { success: true, data: [{ id: 1, name: '手提包' }] })

    await expect(http.get('/categories')).resolves.toEqual([{ id: 1, name: '手提包' }])
  })

  it('已登入時自動帶上 Authorization header', async () => {
    useAuthStore().setSession({ accessToken: 'abc.def.ghi', user: { name: '王小明', role: 'USER' } })
    const sent = respondWith(200, { success: true, data: null })

    await http.get('/users/me')

    expect(sent.config.headers.Authorization).toBe('Bearer abc.def.ghi')
  })

  it('未登入時不帶 Authorization header', async () => {
    const sent = respondWith(200, { success: true, data: null })

    await http.get('/products')

    expect(sent.config.headers.Authorization).toBeUndefined()
  })

  it('錯誤統一轉成 ApiError，可以取得欄位錯誤', async () => {
    respondWith(400, {
      success: false,
      error: {
        code: 'VALIDATION_FAILED',
        message: '輸入資料有誤',
        fieldErrors: [{ field: 'email', message: 'Email 格式不正確' }],
      },
    })

    const error = await http.post('/auth/register', {}).catch((e) => e)

    expect(error).toBeInstanceOf(ApiError)
    expect(error.status).toBe(400)
    expect(error.code).toBe('VALIDATION_FAILED')
    expect(error.fieldMessage('email')).toBe('Email 格式不正確')
    expect(routerMock.push).not.toHaveBeenCalled()
  })

  it('401 時登出並導向登入頁，帶上原本的網址', async () => {
    const auth = useAuthStore()
    auth.setSession({ accessToken: 'expired', user: { name: '王小明', role: 'USER' } })
    respondWith(401, { success: false, error: { code: 'UNAUTHORIZED', message: '請先登入' } })

    await http.get('/orders').catch(() => {})

    expect(auth.isLoggedIn).toBe(false)
    expect(localStorage.getItem('mamashop.token')).toBeNull()
    expect(routerMock.push).toHaveBeenCalledWith({ name: 'login', query: { redirect: '/orders?page=2' } })
    expect(messageMock.warning).toHaveBeenCalledWith('登入已過期，請重新登入')
  })

  it('原本就沒登入的 401：導向登入頁，但不說「登入已過期」', async () => {
    respondWith(401, { success: false, error: { code: 'UNAUTHORIZED', message: '請先登入' } })

    await http.get('/orders').catch(() => {})

    expect(routerMock.push).toHaveBeenCalled()
    expect(messageMock.warning).not.toHaveBeenCalled()
  })

  it('skipAuthRedirect：登入失敗的 401 不要導頁（F04 的登入 API 會用到）', async () => {
    respondWith(401, { success: false, error: { code: 'INVALID_CREDENTIALS', message: 'Email 或密碼錯誤' } })

    const error = await http.post('/auth/login', {}, { skipAuthRedirect: true }).catch((e) => e)

    expect(error.message).toBe('Email 或密碼錯誤')
    expect(routerMock.push).not.toHaveBeenCalled()
  })

  it('後端沒開（Nginx／proxy 回 502，不是 JSON）：提示稍後再試', async () => {
    respondWith(502, '<html>Bad Gateway</html>')

    const error = await http.get('/products').catch((e) => e)

    expect(error.status).toBe(502)
    expect(error.message).toBe('伺服器暫時無法使用，請稍後再試')
  })

  it('其他沒有錯誤內容的回應：一般錯誤訊息', async () => {
    respondWith(500, '')

    const error = await http.get('/products').catch((e) => e)

    expect(error.message).toBe('系統發生錯誤，請稍後再試')
  })

  it('連不上伺服器時給友善的訊息', async () => {
    http.defaults.adapter = async () => {
      throw new Error('Network Error')
    }

    const error = await http.get('/products').catch((e) => e)

    expect(error.code).toBe('NETWORK_ERROR')
    expect(error.message).toBe('無法連線到伺服器，請檢查網路')
  })
})
