// ==================== 分类管理 ====================

/**
 * 分类树节点 VO（对应后端 CategoryVO）
 * 注意：后端 Jackson 已配置 Long→String 序列化，所有 ID 字段为 string 类型，
 * 避免雪花 ID（19 位）在 JS 中精度丢失。
 */
export interface CategoryVO {
  catId: string
  name: string
  parentCid: string
  catLevel: number
  showStatus: number
  sort: number
  icon?: string
  productUnit?: string
  productCount?: number
  children?: CategoryVO[]
}

/**
 * 新增分类 DTO（对应后端 CategorySaveDTO）
 */
export interface CategorySaveDTO {
  name: string
  parentCid: string
  sort?: number
  icon?: string
  productUnit?: string
}

/**
 * 修改分类 DTO（对应后端 CategoryUpdateDTO）
 */
export interface CategoryUpdateDTO {
  catId: string
  name?: string
  sort?: number
  icon?: string
  productUnit?: string
}

/**
 * 拖拽排序项（对应后端 CategorySortDTO.SortItem）
 */
export interface CategorySortItem {
  catId: string
  parentCid: string
  catLevel: number
  sort: number
}

// ==================== 品牌管理 ====================

/**
 * 品牌详情/列表项 VO（对应后端 BrandVO）
 * ID 为 string 类型（后端 Long→String 序列化）。
 */
export interface BrandVO {
  id: string
  name: string
  logo: string
  descript?: string
  showStatus: number
  firstLetter?: string
  sort: number
  /** 乐观锁版本号（编辑时需携带） */
  version?: number
}

/**
 * 新增/修改品牌 DTO（对应后端 BrandSaveDTO，通过校验分组区分新增/修改）
 */
export interface BrandSaveDTO {
  id?: string
  name: string
  logo: string
  descript?: string
  showStatus?: number
  firstLetter?: string
  sort?: number
  version?: number
}

/**
 * 品牌分页查询条件（对应后端 BrandQueryDTO）
 */
export interface BrandQueryDTO {
  pageNum?: number
  pageSize?: number
  name?: string
  firstLetter?: string
  showStatus?: number
}

/**
 * 品牌关联分类列表项（对应后端 BrandRelationVO）
 */
export interface BrandRelationVO {
  id: string
  brandId: string
  brandName: string
  catelogId: string
  catelogName: string
}
