import { describe, expect, it } from 'vitest'
import { availableActions, ORDER_ACTIONS } from '@/utils/adminOrderActions'
import { ORDER_STATUS } from '@/utils/labels'

describe('availableActions（和後端 OrderStatus.canTransitionTo 一致）', () => {
  it('出貨前：確認／出貨、取消', () => {
    expect(availableActions('PENDING_CONFIRM')).toEqual(['confirm', 'cancel'])
    expect(availableActions('PENDING_PAYMENT')).toEqual(['confirm', 'cancel'])
    expect(availableActions('PROCESSING')).toEqual(['ship', 'cancel'])
  })

  it('出貨後：不能取消，只能送達／完成或退回', () => {
    expect(availableActions('SHIPPED')).toEqual(['deliver', 'return'])
    expect(availableActions('DELIVERED')).toEqual(['complete', 'return'])
  })

  it('結束的訂單沒有操作', () => {
    for (const status of ['COMPLETED', 'CANCELLED', 'RETURNED']) expect(availableActions(status)).toEqual([])
  })

  it('每個狀態用到的操作都有定義', () => {
    for (const status of Object.keys(ORDER_STATUS)) {
      for (const key of availableActions(status)) expect(ORDER_ACTIONS[key]?.label).toBeTruthy()
    }
  })
})
