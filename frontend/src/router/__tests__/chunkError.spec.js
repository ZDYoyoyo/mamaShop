import { beforeEach, describe, expect, it, vi } from 'vitest'
import { handleChunkLoadError, isChunkLoadError } from '@/router/chunkError'

const TO = { fullPath: '/cart' }

function fakeStorage() {
  const data = {}
  return { getItem: (k) => data[k] ?? null, setItem: (k, v) => (data[k] = v) }
}

describe('頁面程式下載失敗', () => {
  let deps
  let time

  beforeEach(() => {
    time = 1_000_000
    deps = { storage: fakeStorage(), reload: vi.fn(), notify: vi.fn(), now: () => time }
  })

  it('認得各瀏覽器的錯誤訊息', () => {
    expect(isChunkLoadError(new TypeError('Failed to fetch dynamically imported module: /assets/CartView-a1.js'))).toBe(true)
    expect(isChunkLoadError(new TypeError('Importing a module script failed.'))).toBe(true)
    expect(isChunkLoadError(new TypeError('error loading dynamically imported module'))).toBe(true)
    expect(isChunkLoadError(new Error('Cannot read properties of undefined'))).toBe(false)
  })

  it('第一次失敗：重新整理到要去的頁面（可能剛部署新版本）', () => {
    const handled = handleChunkLoadError(new TypeError('Failed to fetch dynamically imported module'), TO, deps)

    expect(handled).toBe(true)
    expect(deps.reload).toHaveBeenCalledWith('/cart')
    expect(deps.notify).not.toHaveBeenCalled()
  })

  it('重新整理後 10 秒內又失敗：不再重新整理（避免無限重整），改顯示錯誤', () => {
    const error = new TypeError('Failed to fetch dynamically imported module')
    handleChunkLoadError(error, TO, deps)
    time += 3_000

    handleChunkLoadError(error, TO, deps)

    expect(deps.reload).toHaveBeenCalledTimes(1)
    expect(deps.notify).toHaveBeenCalledWith('頁面載入失敗，請檢查網路後再試一次')
  })

  it('隔一段時間再失敗：又可以重新整理一次', () => {
    const error = new TypeError('Failed to fetch dynamically imported module')
    handleChunkLoadError(error, TO, deps)
    time += 60_000

    handleChunkLoadError(error, TO, deps)

    expect(deps.reload).toHaveBeenCalledTimes(2)
  })

  it('儲存空間不能用時也能運作', () => {
    const storage = { getItem: () => { throw new Error('blocked') }, setItem: () => { throw new Error('blocked') } }

    handleChunkLoadError(new TypeError('Importing a module script failed.'), TO, { ...deps, storage })

    expect(deps.reload).toHaveBeenCalledWith('/cart')
  })

  it('其他錯誤不處理', () => {
    expect(handleChunkLoadError(new Error('別的錯誤'), TO, deps)).toBe(false)
    expect(deps.reload).not.toHaveBeenCalled()
  })
})
