import { flushPromises } from '@vue/test-utils'

/**
 * 等畫面「穩定」：除了 Promise，也等 setTimeout 跑完。
 * Element Plus 的表單驗證（async-validator）內部用了 setTimeout，只用 flushPromises() 等不到驗證結果。
 * 會隨時間變化的結果（換頁、驗證訊息出現）再搭配 vi.waitFor(() => expect(...)) 重試斷言。
 */
export async function settle() {
  for (let i = 0; i < 3; i++) {
    await flushPromises()
    await new Promise((resolve) => setTimeout(resolve, 0))
  }
}
