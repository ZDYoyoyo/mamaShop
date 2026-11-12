import { describe, expect, it } from 'vitest'
import { fitWithin } from '@/utils/image'

describe('fitWithin（照片縮小的尺寸）', () => {
  it('長邊縮到 1600，等比例', () => {
    expect(fitWithin(4032, 3024)).toEqual({ width: 1600, height: 1200 }) // iPhone 橫拍
    expect(fitWithin(3024, 4032)).toEqual({ width: 1200, height: 1600 }) // 直拍
  })

  it('本來就比較小的不放大', () => {
    expect(fitWithin(800, 600)).toEqual({ width: 800, height: 600 })
  })
})
