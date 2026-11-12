/**
 * 頁面程式下載失敗的處理。
 *
 * 除了首頁，每一頁的程式都是進到那一頁才下載（router/index.js 的 () => import(...)）。下載可能失敗：
 * 1. 網路不穩（手機訊號差）
 * 2. 網站剛部署新版本：舊版的檔名（含 hash，例如 CartView-a1b2c3.js）已經不存在，
 *    還開著舊頁面的人一換頁就會 404
 * 沒處理的話，畫面完全沒反應，顧客只會覺得「按了沒用」。
 *
 * 做法：重新整理到要去的那一頁，載入最新版本；如果 10 秒內又失敗（真的是網路問題），
 * 就不再重新整理（避免無限重整），改顯示錯誤訊息。
 */

const RELOAD_KEY = 'mamashop.chunkReloadAt'
const RETRY_WINDOW_MS = 10_000

// 各瀏覽器的錯誤訊息不同：Chrome / Edge、Safari、Firefox
const CHUNK_ERROR_PATTERN =
  /Failed to fetch dynamically imported module|Importing a module script failed|error loading dynamically imported module/i

export function isChunkLoadError(error) {
  return CHUNK_ERROR_PATTERN.test(error?.message ?? '')
}

/**
 * @param error  router.onError 收到的錯誤
 * @param to     要前往的路由
 * @param deps   方便測試替換：storage（sessionStorage）、reload（整頁重新載入）、notify（顯示錯誤）、now
 * @returns 是否已處理
 */
export function handleChunkLoadError(error, to, { storage, reload, notify, now = Date.now }) {
  if (!isChunkLoadError(error)) return false

  const lastReload = Number(safeGet(storage, RELOAD_KEY)) || 0
  if (now() - lastReload > RETRY_WINDOW_MS) {
    safeSet(storage, RELOAD_KEY, String(now()))
    reload(to.fullPath)
  } else {
    notify('頁面載入失敗，請檢查網路後再試一次')
  }
  return true
}

// sessionStorage 在無痕模式或被封鎖時可能丟出例外
function safeGet(storage, key) {
  try {
    return storage?.getItem(key)
  } catch {
    return null
  }
}

function safeSet(storage, key, value) {
  try {
    storage?.setItem(key, value)
  } catch {
    // 存不了就算了：最壞情況是網路斷線時多重新整理幾次
  }
}
