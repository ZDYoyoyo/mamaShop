import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/orders', () => ({ getAdminOrder: vi.fn(), runOrderAction: vi.fn() }))

import { getAdminOrder, runOrderAction } from '@/api/admin/orders'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import AdminOrderDetailView from '@/views/admin/AdminOrderDetailView.vue'

function detail(orderOverrides = {}, version = 0) {
  return {
    order: {
      orderNo: 'M1',
      status: 'PENDING_CONFIRM',
      paymentMethod: 'COD',
      paymentStatus: 'UNPAID',
      shippingMethod: 'HOME_DELIVERY',
      recipientName: '王媽媽',
      recipientPhone: '0912345678',
      shippingAddress: '台北市中正區',
      note: '請下午送',
      itemsAmount: 1780,
      shippingFee: 100,
      totalAmount: 1880,
      items: [{ productId: 5, productName: '帆布托特包', unitPrice: 890, quantity: 2, subtotal: 1780 }],
      createdAt: '2026-11-13T02:00:00Z',
      confirmedAt: null,
      shippedAt: null,
      deliveredAt: null,
      completedAt: null,
      cancelledAt: null,
      returnedAt: null,
      trackingNo: null,
      ...orderOverrides,
    },
    customer: { id: 9, name: '王小明', email: 'ming@example.com', phone: '0911111111' },
    histories: [
      { fromStatus: null, toStatus: 'PENDING_CONFIRM', operatorName: '王小明', operatorRole: 'USER', note: null, createdAt: '2026-11-13T02:00:00Z' },
    ],
    version,
  }
}

async function mountDetail() {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push('/admin/orders/M1')
  const wrapper = mount(AdminOrderDetailView, { props: { orderNo: 'M1' }, global: { plugins: [router] }, attachTo: document.body })
  await settle()
  return wrapper
}

const actionLabels = (wrapper) => wrapper.findAll('.actions button').map((b) => b.text())

describe('AdminOrderDetailView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    vi.mocked(getAdminOrder).mockReset()
    vi.mocked(runOrderAction).mockReset()
  })

  it('顯示商品、金額、收件人與下單會員（電話可以直接撥打）、狀態紀錄', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue(detail())

    const wrapper = await mountDetail()

    const text = wrapper.text()
    expect(text).toContain('帆布托特包')
    expect(text).toContain('NT$ 1,880')
    expect(text).toContain('宅配：台北市中正區')
    expect(text).toContain('顧客備註：請下午送')
    expect(wrapper.find('a[href="tel:0912345678"]').exists()).toBe(true)
    expect(wrapper.find('a[href="tel:0911111111"]').exists()).toBe(true)
    expect(text).toContain('建立訂單（待確認）')
    expect(text).toContain('王小明（顧客）')
  })

  it('依狀態顯示操作按鈕', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue(detail())
    expect(actionLabels(await mountDetail())).toEqual(['確認訂單', '取消訂單'])

    vi.mocked(getAdminOrder).mockResolvedValue(detail({ status: 'SHIPPED', trackingNo: 'T1' }))
    expect(actionLabels(await mountDetail())).toEqual(['已送達', '退回'])

    vi.mocked(getAdminOrder).mockResolvedValue(detail({ status: 'COMPLETED', paymentStatus: 'PAID' }))
    const done = await mountDetail()
    expect(done.find('.actions').exists()).toBe(false)
    expect(done.text()).toContain('已付款')
  })

  it('確認訂單：帶目前的版本號送出，畫面換成新狀態（下一次操作用新的版本號）', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue(detail({}, 0))
    vi.mocked(runOrderAction).mockResolvedValue(detail({ status: 'PROCESSING', confirmedAt: '2026-11-13T03:00:00Z' }, 1))
    const wrapper = await mountDetail()

    await wrapper.findAll('.actions button').find((b) => b.text() === '確認訂單').trigger('click')
    await settle()
    document.querySelector('.el-dialog__footer .el-button:last-child').click()
    await settle()

    expect(runOrderAction).toHaveBeenCalledWith('confirm', 'M1', { version: 0, note: null })
    await vi.waitFor(() => expect(actionLabels(wrapper)).toEqual(['出貨', '取消訂單']))
  })

  it('別人剛改過（409）：重新讀取最新狀態', async () => {
    vi.mocked(getAdminOrder)
      .mockResolvedValueOnce(detail({}, 0))
      .mockResolvedValueOnce(detail({ status: 'CANCELLED', cancelledAt: '2026-11-13T03:00:00Z' }, 1))
    vi.mocked(runOrderAction).mockRejectedValue(
      new ApiError({ status: 409, code: 'CONFLICT', message: '資料已被其他人修改，請重新整理後再試' }),
    )
    const wrapper = await mountDetail()

    await wrapper.findAll('.actions button').find((b) => b.text() === '確認訂單').trigger('click')
    await settle()
    document.querySelector('.el-dialog__footer .el-button:last-child').click()
    await settle()

    expect(getAdminOrder).toHaveBeenCalledTimes(2)
    await vi.waitFor(() => expect(wrapper.text()).toContain('訂單已取消'))
    expect(wrapper.find('.actions').exists()).toBe(false)
  })

  it('找不到的訂單編號', async () => {
    vi.mocked(getAdminOrder).mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', message: '找不到訂單' }))

    const wrapper = await mountDetail()

    expect(wrapper.text()).toContain('找不到這筆訂單')
  })
})
