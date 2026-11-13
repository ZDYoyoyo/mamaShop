import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('@/api/admin/orders', () => ({ runOrderAction: vi.fn() }))

import { runOrderAction } from '@/api/admin/orders'
import { ApiError } from '@/api/http'
import { settle } from '@/test-utils'
import OrderActionDialog from '@/components/admin/OrderActionDialog.vue'

const dialog = () => document.querySelector('.el-dialog')
const confirmButton = () => dialog().querySelector('.el-dialog__footer .el-button:last-child')

async function open(action) {
  const wrapper = mount(OrderActionDialog, { props: { action: null, orderNo: 'M1', version: 3 }, attachTo: document.body })
  await wrapper.setProps({ action })
  await settle()
  return wrapper
}

async function typeInto(selector, value) {
  const el = dialog().querySelector(selector)
  el.value = value
  el.dispatchEvent(new Event('input'))
  await settle()
}

describe('OrderActionDialog', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.mocked(runOrderAction).mockReset()
  })

  it('確認訂單：顯示說明，送出版本號與備註，回傳新的詳情', async () => {
    const updated = { order: { status: 'PROCESSING' }, version: 4 }
    vi.mocked(runOrderAction).mockResolvedValue(updated)
    const wrapper = await open('confirm')

    expect(dialog().textContent).toContain('顧客就不能自己取消了')
    await typeInto('textarea', ' 已電話確認 ')
    confirmButton().click()
    await settle()

    expect(runOrderAction).toHaveBeenCalledWith('confirm', 'M1', { version: 3, note: '已電話確認' })
    expect(wrapper.emitted('done')[0][0]).toBe(updated)
    expect(wrapper.emitted('close')).toBeTruthy()
  })

  it('出貨：物流單號必填', async () => {
    await open('ship')

    confirmButton().click()
    await vi.waitFor(() => expect(dialog().textContent).toContain('請填寫物流單號'))
    expect(runOrderAction).not.toHaveBeenCalled()

    vi.mocked(runOrderAction).mockResolvedValue({})
    await typeInto('input', 'T12345')
    confirmButton().click()
    await settle()

    expect(runOrderAction).toHaveBeenCalledWith('ship', 'M1', { version: 3, note: null, trackingNo: 'T12345' })
  })

  it('退回：一定要選是否加回庫存（沒有預設值）', async () => {
    vi.mocked(runOrderAction).mockResolvedValue({})
    await open('return')

    confirmButton().click()
    await vi.waitFor(() => expect(dialog().textContent).toContain('請選擇商品是否加回庫存'))
    expect(runOrderAction).not.toHaveBeenCalled()

    ;[...dialog().querySelectorAll('.el-radio')].find((r) => r.textContent.includes('不要')).click()
    await settle()
    confirmButton().click()
    await settle()

    expect(runOrderAction).toHaveBeenCalledWith('return', 'M1', { version: 3, note: null, restock: false })
  })

  it('409（別人剛改過）：通知父元件重新讀取，關閉對話框', async () => {
    vi.mocked(runOrderAction).mockRejectedValue(
      new ApiError({ status: 409, code: 'CONFLICT', message: '資料已被其他人修改，請重新整理後再試' }),
    )
    const wrapper = await open('cancel')

    confirmButton().click()
    await settle()

    expect(wrapper.emitted('stale')[0][0]).toBe('資料已被其他人修改，請重新整理後再試')
    expect(wrapper.emitted('close')).toBeTruthy()
    expect(wrapper.emitted('done')).toBeFalsy()
  })

  it('其他錯誤（網路）：顯示在對話框裡，不關閉', async () => {
    vi.mocked(runOrderAction).mockRejectedValue(new ApiError({ status: 0, code: 'NETWORK_ERROR', message: '無法連線到伺服器，請檢查網路' }))
    const wrapper = await open('deliver')

    confirmButton().click()
    await settle()

    expect(dialog().textContent).toContain('無法連線到伺服器')
    expect(wrapper.emitted('close')).toBeFalsy()
  })
})
