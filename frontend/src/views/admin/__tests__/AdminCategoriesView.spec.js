import { beforeEach, describe, expect, it, vi } from 'vitest'
import { mount } from '@vue/test-utils'

vi.mock('@/api/admin/categories', () => ({
  listAdminCategories: vi.fn(),
  createCategory: vi.fn(),
  updateCategory: vi.fn(),
  deleteCategory: vi.fn(),
}))

import { createCategory, deleteCategory, listAdminCategories, updateCategory } from '@/api/admin/categories'
import { ApiError } from '@/api/http'
import { settle } from '@/test-utils'
import AdminCategoriesView from '@/views/admin/AdminCategoriesView.vue'

const CATEGORIES = [
  { id: 1, name: '手提包', sortOrder: 1 },
  { id: 2, name: '布料', sortOrder: 4 },
]

async function mountView() {
  const wrapper = mount(AdminCategoriesView, { attachTo: document.body })
  await settle()
  return wrapper
}

const dialog = () => document.querySelector('.el-dialog')
const dialogButton = (text) => [...dialog().querySelectorAll('button')].find((b) => b.textContent.trim() === text)

async function fillName(value) {
  const input = dialog().querySelector('input')
  input.value = value
  input.dispatchEvent(new Event('input'))
  await settle()
}

describe('AdminCategoriesView', () => {
  beforeEach(() => {
    document.body.innerHTML = ''
    for (const fn of [listAdminCategories, createCategory, updateCategory, deleteCategory]) vi.mocked(fn).mockReset()
    vi.mocked(listAdminCategories).mockResolvedValue(CATEGORIES)
  })

  it('列出分類與排序', async () => {
    const wrapper = await mountView()

    const rows = wrapper.findAll('.row')
    expect(rows).toHaveLength(2)
    expect(rows[1].text()).toContain('布料')
    expect(rows[1].text()).toContain('排序 4')
  })

  it('新增：預設排在最後，送出後重新讀取', async () => {
    vi.mocked(createCategory).mockResolvedValue({ id: 3, name: '材料包', sortOrder: 5 })
    const wrapper = await mountView()

    await wrapper.findAll('button').find((b) => b.text() === '新增分類').trigger('click')
    await settle()
    await fillName('  材料包 ')
    dialogButton('儲存').click()
    await settle()

    expect(createCategory).toHaveBeenCalledWith({ name: '材料包', sortOrder: 5 })
    expect(listAdminCategories).toHaveBeenCalledTimes(2)
  })

  it('名稱重複：顯示在名稱欄位下方，對話框不關', async () => {
    vi.mocked(updateCategory).mockRejectedValue(new ApiError({ status: 409, code: 'CATEGORY_NAME_EXISTS', message: '分類名稱已存在' }))
    const wrapper = await mountView()

    await wrapper.findAll('.row')[1].findAll('button').find((b) => b.text() === '修改').trigger('click')
    await settle()
    await fillName('手提包')
    dialogButton('儲存').click()

    await vi.waitFor(() => expect(dialog().querySelector('.el-form-item__error')?.textContent).toContain('分類名稱已存在'))
    expect(updateCategory).toHaveBeenCalledWith(2, { name: '手提包', sortOrder: 4 })
  })

  it('沒填名稱不送出', async () => {
    const wrapper = await mountView()

    await wrapper.findAll('button').find((b) => b.text() === '新增分類').trigger('click')
    await settle()
    dialogButton('儲存').click()

    await vi.waitFor(() => expect(dialog().textContent).toContain('請填寫分類名稱'))
    expect(createCategory).not.toHaveBeenCalled()
  })

  it('對話框表單關掉瀏覽器內建檢查', async () => {
    const wrapper = await mountView()

    await wrapper.findAll('button').find((b) => b.text() === '新增分類').trigger('click')
    await settle()

    expect(dialog().querySelector('form').hasAttribute('novalidate')).toBe(true)
  })

  it('刪除：確認後呼叫 API 並從列表移除', async () => {
    vi.mocked(deleteCategory).mockResolvedValue(null)
    const wrapper = await mountView()

    await wrapper.findAll('.row')[1].findAll('button').find((b) => b.text() === '刪除').trigger('click')
    await settle()
    ;[...document.querySelectorAll('.el-popconfirm button')].find((b) => b.textContent.trim() === '刪除').click()
    await settle()

    expect(deleteCategory).toHaveBeenCalledWith(2)
    expect(wrapper.findAll('.row')).toHaveLength(1)
  })

  it('底下還有商品不能刪：保留在列表', async () => {
    vi.mocked(deleteCategory).mockRejectedValue(
      new ApiError({ status: 409, code: 'CATEGORY_HAS_PRODUCTS', message: '分類底下還有商品（包含下架的），請先把商品改到其他分類' }),
    )
    const wrapper = await mountView()

    await wrapper.findAll('.row')[0].findAll('button').find((b) => b.text() === '刪除').trigger('click')
    await settle()
    ;[...document.querySelectorAll('.el-popconfirm button')].find((b) => b.textContent.trim() === '刪除').click()
    await settle()

    expect(deleteCategory).toHaveBeenCalledWith(1)
    expect(wrapper.findAll('.row')).toHaveLength(2)
  })
})
