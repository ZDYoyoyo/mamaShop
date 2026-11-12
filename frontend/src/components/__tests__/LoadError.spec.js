import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import LoadError from '@/components/LoadError.vue'

describe('LoadError', () => {
  it('顯示原因，按「重新載入」發出 retry', async () => {
    const wrapper = mount(LoadError, { props: { message: '無法連線到伺服器，請檢查網路' } })

    expect(wrapper.text()).toContain('載入失敗')
    expect(wrapper.text()).toContain('無法連線到伺服器，請檢查網路')

    await wrapper.find('button').trigger('click')

    expect(wrapper.emitted('retry')).toHaveLength(1)
  })
})
