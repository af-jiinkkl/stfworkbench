/**
 * 后端统一返回体，见 docs/接口清单.md §1.1
 */
export interface ApiResult<T = unknown> {
  code: number
  msg: string
  data: T
}

/**
 * 分页返回结构，见 docs/接口清单.md §1.3
 */
export interface PageResult<T> {
  total: number
  pageNum: number
  pageSize: number
  records: T[]
}
