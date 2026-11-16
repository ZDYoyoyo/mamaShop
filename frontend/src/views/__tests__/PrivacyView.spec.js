import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import { routes } from '@/router'
import { SHOP } from '@/config/shop'
import PrivacyView from '@/views/PrivacyView.vue'

describe('PrivacyView', () => {
  it('說明蒐集的資料、用途、提供對象、權利與聯絡方式', () => {
    const wrapper = mount(PrivacyView)
    const text = wrapper.text()

    for (const heading of ['我們蒐集哪些資料', '資料用來做什麼', '資料會提供給誰', '您的權利', '聯絡我們']) {
      expect(text).toContain(heading)
    }
    expect(text).toContain('信用卡號不會經過、也不會儲存在本網站')
    expect(wrapper.find(`a[href="mailto:${SHOP.contactEmail}"]`).exists()).toBe(true)
  })

  it('商店資料還是暫定值時顯示提醒（改成真實資料並把 placeholder 設成 false 後才會消失）', () => {
    const wrapper = mount(PrivacyView)

    expect(wrapper.find('.draft').exists()).toBe(SHOP.placeholder)
  })

  it('不用登入就能看', () => {
    const route = routes.find((r) => r.name === 'privacy')
    expect(route.path).toBe('/privacy')
    expect(route.meta?.requiresAuth).toBeFalsy()
  })
})
