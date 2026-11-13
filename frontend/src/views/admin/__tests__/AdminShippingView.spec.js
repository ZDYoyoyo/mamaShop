import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('@/api/admin/shipping', () => ({ listAdminShippingMethods: vi.fn(), updateShippingMethod: vi.fn() }))

import { listAdminShippingMethods, updateShippingMethod } from '@/api/admin/shipping'
import { ApiError } from '@/api/http'
import { settle } from '@/test-utils'
import AdminShippingView from '@/views/admin/AdminShippingView.vue'

const HOME = { id: 1, code: 'HOME_DELIVERY', name: '宅配', fee: 100, enabled: true, sortOrder: 1 }
const CVS = { id: 2, code: 'CVS_PICKUP', name: '超商取貨', fee: 60, enabled: true, sortOrder: 2 }

async function mountView() {
  const wrapper = mount(AdminShippingView, { attachTo: document.body })
  await settle()
  return wrapper
}

const saveButton = (card) => card.findAll('button').find((b) => b.text() === '儲存')

async function setFee(card, value) {
  const input = card.find('.number input')
  await input.setValue(String(value))
  await input.trigger('change')
  await settle()
}

describe('AdminShippingView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    vi.mocked(updateShippingMethod).mockReset()
    vi.mocked(listAdminShippingMethods).mockResolvedValue([HOME, CVS])
  })

  it('每種運送方式一張卡片，沒改東西時不能儲存', async () => {
    const wrapper = await mountView()

    const cards = wrapper.findAll('.card')
    expect(cards).toHaveLength(2)
    expect(cards[0].text()).toContain('啟用中・NT$ 100')
    expect(saveButton(cards[0]).attributes('disabled')).toBeDefined()
  })

  it('表單關掉瀏覽器內建檢查（否則 65 不是 step 10 的倍數，瀏覽器會擋下送出）', async () => {
    const wrapper = await mountView()

    expect(wrapper.findAll('form').every((f) => f.attributes('novalidate') !== undefined)).toBe(true)
  })

  it('改運費後儲存：送出整筆設定，顯示新運費', async () => {
    vi.mocked(updateShippingMethod).mockResolvedValue({ ...HOME, fee: 120 })
    const wrapper = await mountView()
    const card = wrapper.findAll('.card')[0]

    await setFee(card, 120)
    await saveButton(card).trigger('click')
    await settle()

    expect(updateShippingMethod).toHaveBeenCalledWith(1, { name: '宅配', fee: 120, enabled: true, sortOrder: 1 })
    expect(card.text()).toContain('啟用中・NT$ 120')
    expect(saveButton(card).attributes('disabled')).toBeDefined()
  })

  it('還原：回到儲存的值', async () => {
    const wrapper = await mountView()
    const card = wrapper.findAll('.card')[1]

    await setFee(card, 99)
    await card.findAll('button').find((b) => b.text() === '還原').trigger('click')
    await settle()

    expect(card.find('.number input').element.value).toBe('60')
  })

  it('只剩一種啟用中：開關不能關', async () => {
    vi.mocked(listAdminShippingMethods).mockResolvedValue([HOME, { ...CVS, enabled: false }])

    const wrapper = await mountView()

    const card = wrapper.findAll('.card')[0]
    expect(card.find('.el-switch').classes()).toContain('is-disabled')
    expect(card.text()).toContain('唯一啟用中的方式，不能停用')
  })

  it('後端拒絕（例如另一位管理員剛停用了另一種）：顯示原因，保留輸入', async () => {
    vi.mocked(updateShippingMethod).mockRejectedValue(
      new ApiError({ status: 409, code: 'LAST_SHIPPING_METHOD', message: '至少要保留一種啟用中的運送方式' }),
    )
    const wrapper = await mountView()
    const card = wrapper.findAll('.card')[1]

    await card.find('.el-switch').trigger('click')
    await settle()
    await saveButton(card).trigger('click')
    await settle()

    expect(card.text()).toContain('至少要保留一種啟用中的運送方式')
    expect(card.find('.el-switch').classes()).not.toContain('is-checked')
    expect(card.text()).toContain('啟用中・NT$ 60')
  })
})
