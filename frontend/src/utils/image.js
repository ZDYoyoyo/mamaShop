/**
 * 上傳前在瀏覽器裡處理商品照片：縮小＋重新存成 JPG。
 *
 * 為什麼要做（B07 的 NOTES 留給 A02 的事）：
 * 1. 手機照片 3～5MB，商品列表會很慢 → 長邊縮到 1600px，通常剩幾百 KB
 * 2. 手機照片的 EXIF 可能有拍攝地點（GPS），在家拍的商品照會洩漏住址
 *    → 用 canvas 重畫一次再輸出，只會留下像素，EXIF 全部不見
 * 3. iPhone 的 HEIC 格式後端不收 → Safari 讀得懂 HEIC，重新輸出就變成 JPG
 */

export const MAX_IMAGE_SIDE = 1600
const JPEG_QUALITY = 0.85

/** 等比例縮小到長邊不超過 max；本來就比較小的不放大 */
export function fitWithin(width, height, max = MAX_IMAGE_SIDE) {
  const scale = Math.min(1, max / Math.max(width, height))
  return { width: Math.round(width * scale), height: Math.round(height * scale) }
}

/**
 * @param {File} file 使用者選的圖片
 * @returns {Promise<Blob>} JPG
 * @throws Error 瀏覽器無法讀取這個檔案（不是圖片，或格式不支援）
 */
export async function compressImage(file) {
  let bitmap
  try {
    // imageOrientation: 'from-image'：依照 EXIF 的方向轉正（直拍的照片不會變橫的）
    bitmap = await createImageBitmap(file, { imageOrientation: 'from-image' })
  } catch {
    throw new Error('無法讀取這張圖片，請改用 JPG 或 PNG')
  }

  const { width, height } = fitWithin(bitmap.width, bitmap.height)
  const canvas = document.createElement('canvas')
  canvas.width = width
  canvas.height = height
  const context = canvas.getContext('2d')
  // PNG 透明的地方存成 JPG 會變黑色，先鋪白底
  context.fillStyle = '#ffffff'
  context.fillRect(0, 0, width, height)
  context.drawImage(bitmap, 0, 0, width, height)
  bitmap.close?.()

  return new Promise((resolve, reject) => {
    canvas.toBlob(
      (blob) => (blob ? resolve(blob) : reject(new Error('圖片處理失敗，請換一張試試'))),
      'image/jpeg',
      JPEG_QUALITY,
    )
  })
}
