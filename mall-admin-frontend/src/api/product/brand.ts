import { get, post, put, del } from '@/utils/request'
import type { PageVO } from '@/api/types/common'
import type { BrandVO, BrandSaveDTO, BrandQueryDTO, BrandRelationVO } from '@/api/types/product'

/** 分页查询品牌 */
export function getBrandPage(params: BrandQueryDTO) {
  return get<PageVO<BrandVO>>('/product/brand', { params })
}

/** 品牌详情 */
export function getBrandDetail(id: string) {
  return get<BrandVO>(`/product/brand/${id}`)
}

/** 新增品牌 */
export function createBrand(data: BrandSaveDTO) {
  return post<void>('/product/brand', data)
}

/** 修改品牌 */
export function updateBrand(data: BrandSaveDTO) {
  return put<void>('/product/brand', data)
}

/** 更新显示状态 */
export function updateBrandShowStatus(id: string, showStatus: number) {
  return put<void>(`/product/brand/${id}/show-status`, { showStatus })
}

/** 删除品牌 */
export function deleteBrand(id: string) {
  return del<void>(`/product/brand/${id}`)
}

/** 批量删除品牌 */
export function batchDeleteBrands(ids: string[]) {
  return del<void>('/product/brand/batch', { data: { ids } })
}

/** 查询品牌关联分类列表 */
export function getBrandRelations(brandId: string) {
  return get<BrandRelationVO[]>(`/product/brand/${brandId}/category`)
}

/** 新增品牌-分类关联 */
export function createBrandRelation(brandId: string, categoryId: string) {
  return post<void>('/product/brand/category', { brandId, categoryId })
}

/** 移除品牌-分类关联 */
export function deleteBrandRelation(brandId: string, categoryId: string) {
  return del<void>(`/product/brand/${brandId}/category/${categoryId}`)
}
