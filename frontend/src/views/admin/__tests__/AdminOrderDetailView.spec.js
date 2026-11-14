import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/orders', () => ({
  getAdminOrder: vi.fn(),
  runOrderAction: vi.fn(),
  getShipment: vi.fn(),
  createShipment: vi.fn(),
  openShipmentLabel: vi.fn(),
}))
vi.mock('@/api/logistics', () => ({ getLogisticsOptions: vi.fn() }))

import { createShipment, getAdminOrder, getShipment, openShipmentLabel, runOrderAction } from '@/api/admin/orders'
import { getLogisticsOptions } from '@/api/logistics'
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
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: false, shipmentEnabled: false })
    vi.mocked(getShipment).mockResolvedValue(null)
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

describe('AdminOrderDetailView 綠界物流單（P03）', () => {
  const cvsOrder = (overrides = {}, version = 1) =>
    detail({ status: 'PROCESSING', shippingMethod: 'CVS_PICKUP', cvsBrand: 'SEVEN', cvsStoreCode: '131386', cvsStoreName: '建盛門市', ...overrides }, version)
  const SHIPMENT = {
    logisticsId: '3799461', logisticsSubType: 'UNIMARTC2C', paymentNo: 'D8858181', validationNo: '5655',
    statusCode: 300, statusMessage: '訂單處理中(綠界已收到訂單資料)', statusUpdatedAt: '2026-11-14T07:18:11Z', createdAt: '2026-11-14T07:18:11Z',
  }

  beforeEach(() => {
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    vi.mocked(getAdminOrder).mockReset()
    vi.mocked(getShipment).mockReset()
    vi.mocked(getShipment).mockResolvedValue(null)
    vi.mocked(createShipment).mockReset()
    vi.mocked(openShipmentLabel).mockReset()
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: true, shipmentEnabled: true })
  })

  async function confirmPopconfirm(wrapper) {
    await wrapper.find('button.create-shipment').trigger('click')
    await settle()
    ;[...document.querySelectorAll('.el-popconfirm button')].find((b) => b.textContent.includes('建立')).click()
    await settle()
  }

  it('備貨中的超商訂單：建立物流單並出貨，之後顯示寄件代碼', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue(cvsOrder())
    vi.mocked(createShipment).mockResolvedValue(cvsOrder({ status: 'SHIPPED', trackingNo: 'D88581815655' }, 2))
    const wrapper = await mountDetail()
    vi.mocked(getShipment).mockResolvedValue(SHIPMENT)

    await confirmPopconfirm(wrapper)

    expect(createShipment).toHaveBeenCalledWith('M1', { version: 1 })
    await vi.waitFor(() => expect(wrapper.find('.shipment').text()).toContain('寄件代碼 D8858181'))
    expect(wrapper.find('.shipment').text()).toContain('驗證碼 5655')
    expect(wrapper.find('.shipment').text()).toContain('ibon')
    expect(document.body.textContent).toContain('已建立物流單，寄件代碼 D88581815655')
  })

  it('綠界拒絕：顯示綠界的原因，訂單不變', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue(cvsOrder())
    vi.mocked(createShipment).mockRejectedValue(
      new ApiError({ status: 502, code: 'SHIPMENT_CREATE_FAILED', message: '綠界建立物流單失敗：收件人姓名請設定為 4~10 字元' }),
    )
    const wrapper = await mountDetail()

    await confirmPopconfirm(wrapper)

    await vi.waitFor(() => expect(document.body.textContent).toContain('收件人姓名請設定為 4~10 字元'))
    expect(wrapper.find('button.create-shipment').exists()).toBe(true)
  })

  it('已有物流單：顯示最新物流狀態，可以列印寄件單', async () => {
    vi.mocked(getAdminOrder).mockResolvedValue(cvsOrder({ status: 'DELIVERED' }, 3))
    vi.mocked(getShipment).mockResolvedValue({ ...SHIPMENT, statusCode: 2073, statusMessage: '包裹配達取件門市' })
    const wrapper = await mountDetail()

    expect(wrapper.find('.shipment').text()).toContain('物流狀態：包裹配達取件門市')
    await wrapper.find('.shipment button.print').trigger('click')
    expect(openShipmentLabel).toHaveBeenCalledWith('M1')
  })

  it('沒設定綠界物流、宅配訂單：不顯示物流單區塊', async () => {
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: true, shipmentEnabled: false })
    vi.mocked(getAdminOrder).mockResolvedValue(cvsOrder())
    let wrapper = await mountDetail()
    expect(wrapper.find('.shipment').exists()).toBe(false)

    document.body.innerHTML = ''
    vi.mocked(getShipment).mockClear()
    vi.mocked(getLogisticsOptions).mockResolvedValue({ cvsMapEnabled: true, shipmentEnabled: true })
    vi.mocked(getAdminOrder).mockResolvedValue(detail({ status: 'PROCESSING' }, 1))
    wrapper = await mountDetail()
    expect(wrapper.find('.shipment').exists()).toBe(false)
    expect(getShipment).not.toHaveBeenCalled()
  })
})
