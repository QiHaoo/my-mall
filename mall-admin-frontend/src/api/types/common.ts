/**
 * 统一响应结构 R<T>
 * 对应后端 com.mymall.common.result.R
 */
export interface R<T> {
  code: number
  msg: string
  data: T
}

/**
 * 分页响应 PageVO<T>
 * 对应后端 com.mymall.common.result.PageVO
 *
 * 注意：后端 Jackson 配置了 Long→String 序列化，分页字段（total/current/size/pages）
 * 也为 string 类型。前端使用时需用 Number() 转换。
 */
export interface PageVO<T> {
  records: T[]
  total: string
  size: string
  current: string
  pages: string
}

/**
 * 分页查询基础参数
 */
export interface PageQuery {
  pageNum?: number
  pageSize?: number
}
