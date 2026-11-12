import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('@/api/admin/products', () => ({ adjustProductStock: vi.fn() }))

import { adjustProductStock } from '@/api/admin/products'
import { ApiError } from '@/api/http'
import { settle } from '@/test-utils'
import StockAdjustDialog from '@/components/admin/StockAdjustDialog.vue'

const PRODUCT = { id: 7, name: '帆布托特包', stock: 10 }

/** 對話框會被放到 body 底下，所以從 document 找元素 */
const $ = (selector) => document.querySelector(selector)
const buttonByText = (text) => [...document.querySelectorAll('.el-dialog button')].find((b) => b.textContent.trim() === text)

async function open() {
  const wrapper = mount(StockAdjustDialog, { props: { product: null }, attachTo: document.body })
  await wrapper.setProps({ product: PRODUCT })
  await settle()
  return wrapper
}

async function setAmount(value) {
  const input = $('.el-dialog .el-input-number input')
  input.value = String(value)
  input.dispatchEvent(new Event('input'))
  input.dispatchEvent(new Event('change'))
  await settle()
}

async function choose(text) {
  ;[...document.querySelectorAll('.el-dialog .el-radio-button')].find((b) => b.textContent.includes(text)).click()
  await settle()
}

describe('StockAdjustDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.mocked(adjustProductStock).mockReset()
  })

  it('進貨：預覽調整後數量，送出正的增減量', async () => {
    vi.mocked(adjustProductStock).mockResolvedValue({ ...PRODUCT, stock: 15 })
    const wrapper = await open()

    await setAmount(5)
    expect($('.el-dialog').textContent).toContain('調整後：15 件')
    buttonByText('確定').click()
    await settle()

    expect(adjustProductStock).toHaveBeenCalledWith(7, 5)
    expect(wrapper.emitted('adjusted')[0][0].stock).toBe(15)
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('減少：送出負的增減量', async () => {
    vi.mocked(adjustProductStock).mockResolvedValue({ ...PRODUCT, stock: 7 })
    await open()

    await choose('減少')
    await setAmount(3)
    buttonByText('確定').click()
    await settle()

    expect(adjustProductStock).toHaveBeenCalledWith(7, -3)
  })

  it('減少超過目前庫存：提示並不能送出', async () => {
    await open()

    await choose('減少')
    await setAmount(11)

    expect($('.el-dialog').textContent).toContain('庫存不夠減')
    expect(buttonByText('確定').disabled).toBe(true)
  })

  it('後端拒絕（例如這段時間被買走了）：顯示原因，不關閉', async () => {
    vi.mocked(adjustProductStock).mockRejectedValue(
      new ApiError({ status: 409, code: 'STOCK_ADJUSTMENT_INVALID', message: '調整後庫存不可小於 0' }),
    )
    const wrapper = await open()

    await choose('減少')
    await setAmount(10)
    buttonByText('確定').click()
    await settle()

    expect($('.el-dialog').textContent).toContain('調整後庫存不可小於 0')
    expect(wrapper.emitted('close')).toBeFalsy()
  })
})
