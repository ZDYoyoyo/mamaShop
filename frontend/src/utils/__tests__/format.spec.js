import { describe, expect, it } from 'vitest'
import { formatPrice, stockLabel } from '@/utils/format'

describe('formatPrice', () => {
  it('整數不顯示小數，千分位加逗號', () => {
    expect(formatPrice(890)).toBe('NT$ 890')
    expect(formatPrice(1280)).toBe('NT$ 1,280')
  })

  it('有小數時最多兩位', () => {
    expect(formatPrice(150.5)).toBe('NT$ 150.5')
    expect(formatPrice('99.99')).toBe('NT$ 99.99')
  })

  it('沒有值時回傳空字串', () => {
    expect(formatPrice(null)).toBe('')
    expect(formatPrice(undefined)).toBe('')
  })
})

describe('stockLabel', () => {
  it('售完', () => {
    expect(stockLabel(0)).toEqual({ text: '已售完', type: 'info' })
  })

  it('剩少量時提醒', () => {
    expect(stockLabel(1)).toEqual({ text: '僅剩 1 件', type: 'warning' })
    expect(stockLabel(5)).toEqual({ text: '僅剩 5 件', type: 'warning' })
  })

  it('庫存充足時不顯示', () => {
    expect(stockLabel(6)).toBeNull()
  })
})
