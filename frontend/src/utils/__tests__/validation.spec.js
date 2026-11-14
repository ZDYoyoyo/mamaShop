import { describe, expect, it } from 'vitest'
import { isValidCvsName } from '@/utils/validation'

/** 超商取貨的姓名規則：和後端 CvsNameRule 一致（每個例子都在綠界測試環境實際送過） */
describe('isValidCvsName', () => {
  it.each(['王小明', '王 小明', 'Johnny', 'John Smith', '王Amy', '歐陽娜娜'])('%s 可以', (name) => {
    expect(isValidCvsName(name)).toBe(true)
  })

  it.each(['林', 'Tom', '歐陽美麗娜子', 'JohnnySmith', '王小明2', '王.小明', '', null])('%s 不行', (name) => {
    expect(isValidCvsName(name)).toBe(false)
  })
})
