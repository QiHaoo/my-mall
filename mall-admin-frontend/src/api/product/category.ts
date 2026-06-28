import { get, post, put } from '@/utils/request'
import type {
  CategoryVO,
  CategorySaveDTO,
  CategoryUpdateDTO,
  CategorySortItem
} from '@/api/types/product'

/** 分类树查询 */
export function getCategoryTree() {
  return get<CategoryVO[]>('/product/category/tree')
}

/** 新增分类 */
export function createCategory(data: CategorySaveDTO) {
  return post<void>('/product/category', data)
}

/** 修改分类 */
export function updateCategory(data: CategoryUpdateDTO) {
  return put<void>('/product/category', data)
}

/** 拖拽排序 */
export function sortCategories(categories: CategorySortItem[]) {
  return put<void>('/product/category/sort', { categories })
}

/** 批量删除分类 */
export function batchDeleteCategories(ids: string[]) {
  return post<void>('/product/category/batch-delete', { ids })
}
