import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'
import { createMemoryHistory, createRouter } from 'vue-router'
import { createPinia, setActivePinia } from 'pinia'

vi.mock('@/api/admin/products', () => ({
  getAdminProduct: vi.fn(),
  createProduct: vi.fn(),
  updateProduct: vi.fn(),
  uploadProductImage: vi.fn(),
}))
vi.mock('@/api/catalog', () => ({ getCategories: vi.fn() }))
// jsdom 沒有 canvas：圖片壓縮換成假的（壓縮本身在瀏覽器實測）
vi.mock('@/utils/image', () => ({ compressImage: vi.fn() }))

import { createProduct, getAdminProduct, updateProduct, uploadProductImage } from '@/api/admin/products'
import { getCategories } from '@/api/catalog'
import { ApiError } from '@/api/http'
import { routes } from '@/router'
import { settle } from '@/test-utils'
import { compressImage } from '@/utils/image'
import AdminProductFormView from '@/views/admin/AdminProductFormView.vue'

const EXISTING = {
  id: 7,
  categoryId: 2,
  categoryName: '布料',
  name: '日本棉麻布',
  description: '寬幅 110 cm',
  price: 180,
  stock: 30,
  imageUrl: '/uploads/products/a.jpg',
  status: 'ON_SALE',
}

async function mountForm(url, props = {}) {
  const router = createRouter({ history: createMemoryHistory(), routes })
  await router.push(url)
  const wrapper = mount(AdminProductFormView, { props, global: { plugins: [router] }, attachTo: document.body })
  await settle()
  return { wrapper, router }
}

async function type(wrapper, selector, value) {
  const input = wrapper.find(selector)
  await input.setValue(value)
  await input.trigger('change')
  await input.trigger('blur')
}

async function chooseCategory(name) {
  document.querySelector('.el-select__wrapper').click()
  await settle()
  ;[...document.querySelectorAll('.el-select-dropdown__item')].find((li) => li.textContent.includes(name)).click()
  await settle()
}

async function pickFile(wrapper) {
  const input = wrapper.find('input[type=file]')
  Object.defineProperty(input.element, 'files', { value: [new File(['x'], 'IMG_0001.HEIC')], configurable: true })
  await input.trigger('change')
  await settle()
}

describe('AdminProductFormView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    setActivePinia(createPinia())
    for (const fn of [createProduct, getAdminProduct, updateProduct, uploadProductImage, compressImage]) vi.mocked(fn).mockReset()
    vi.mocked(getCategories).mockResolvedValue([
      { id: 1, name: '手提包' },
      { id: 2, name: '布料' },
    ])
    URL.createObjectURL = vi.fn(() => 'blob:preview')
    URL.revokeObjectURL = vi.fn()
  })

  it('表單關掉瀏覽器內建檢查（否則 1299、1280.5 這種價格會被瀏覽器擋下）', async () => {
    const { wrapper } = await mountForm('/admin/products/new')

    expect(wrapper.find('form').attributes('novalidate')).toBeDefined()
  })

  it('新增：沒填必填欄位時不送出', async () => {
    const { wrapper } = await mountForm('/admin/products/new')

    await wrapper.find('form').trigger('submit')
    await vi.waitFor(() => expect(wrapper.text()).toContain('請填寫商品名稱'))

    expect(wrapper.text()).toContain('請選擇分類')
    expect(createProduct).not.toHaveBeenCalled()
  })

  it('新增：送出基本資料與初始庫存，再上傳壓縮後的圖片，回到列表', async () => {
    const compressed = new Blob(['jpg'], { type: 'image/jpeg' })
    vi.mocked(compressImage).mockResolvedValue(compressed)
    vi.mocked(createProduct).mockResolvedValue({ ...EXISTING, id: 9, status: 'OFF_SALE' })
    vi.mocked(uploadProductImage).mockResolvedValue({})
    const { wrapper, router } = await mountForm('/admin/products/new')

    await chooseCategory('手提包')
    await type(wrapper, '.el-form-item:nth-of-type(2) input', '  新包包  ')
    await type(wrapper, '.number input', '1280')
    await pickFile(wrapper)
    expect(wrapper.text()).toContain('已壓縮為')
    await wrapper.find('form').trigger('submit')
    await settle()

    expect(createProduct).toHaveBeenCalledWith({ categoryId: 1, name: '新包包', description: null, price: 1280, stock: 0 })
    expect(uploadProductImage).toHaveBeenCalledWith(9, compressed)
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('admin-products'))
  })

  it('新增成功但圖片上傳失敗：帶到編輯頁（避免重複建立）', async () => {
    vi.mocked(compressImage).mockResolvedValue(new Blob(['jpg']))
    vi.mocked(createProduct).mockResolvedValue({ ...EXISTING, id: 9 })
    vi.mocked(uploadProductImage).mockRejectedValue(new ApiError({ status: 0, code: 'TIMEOUT', message: '連線逾時，請稍後再試' }))
    const { wrapper, router } = await mountForm('/admin/products/new')

    await chooseCategory('手提包')
    await type(wrapper, '.el-form-item:nth-of-type(2) input', '新包包')
    await type(wrapper, '.number input', '100')
    await pickFile(wrapper)
    await wrapper.find('form').trigger('submit')
    await settle()

    await vi.waitFor(() => expect(router.currentRoute.value.fullPath).toBe('/admin/products/9/edit'))
  })

  it('選到讀不了的檔案：顯示原因', async () => {
    vi.mocked(compressImage).mockRejectedValue(new Error('無法讀取這張圖片，請改用 JPG 或 PNG'))
    const { wrapper } = await mountForm('/admin/products/new')

    await pickFile(wrapper)

    expect(wrapper.text()).toContain('無法讀取這張圖片')
  })

  it('編輯：帶入目前資料，庫存只顯示不能改，送出不含庫存', async () => {
    vi.mocked(getAdminProduct).mockResolvedValue(EXISTING)
    vi.mocked(updateProduct).mockResolvedValue({ ...EXISTING, price: 200 })
    const { wrapper, router } = await mountForm('/admin/products/7/edit', { id: '7' })

    expect(wrapper.find('.el-form-item:nth-of-type(2) input').element.value).toBe('日本棉麻布')
    expect(wrapper.text()).toContain('30 件・上架中')
    expect(wrapper.findAll('.number')).toHaveLength(1) // 只有價格，沒有庫存輸入框

    await type(wrapper, '.number input', '200')
    await wrapper.find('form').trigger('submit')
    await settle()

    expect(updateProduct).toHaveBeenCalledWith('7', { categoryId: 2, name: '日本棉麻布', description: '寬幅 110 cm', price: 200 })
    expect(uploadProductImage).not.toHaveBeenCalled()
    await vi.waitFor(() => expect(router.currentRoute.value.name).toBe('admin-products'))
  })

  it('後端的欄位錯誤顯示在欄位下方', async () => {
    vi.mocked(getAdminProduct).mockResolvedValue(EXISTING)
    vi.mocked(updateProduct).mockRejectedValue(
      new ApiError({ status: 400, code: 'VALIDATION_FAILED', message: '輸入資料有誤', fieldErrors: [{ field: 'name', message: '名稱不可空白' }] }),
    )
    const { wrapper } = await mountForm('/admin/products/7/edit', { id: '7' })

    await wrapper.find('form').trigger('submit')

    await vi.waitFor(() => expect(wrapper.text()).toContain('名稱不可空白'))
  })

  it('編輯不存在的商品：找不到', async () => {
    vi.mocked(getAdminProduct).mockRejectedValue(new ApiError({ status: 404, code: 'NOT_FOUND', message: '找不到商品' }))

    const { wrapper } = await mountForm('/admin/products/999/edit', { id: '999' })

    expect(wrapper.text()).toContain('找不到這個商品')
  })
})
