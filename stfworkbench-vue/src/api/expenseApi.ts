import { http } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type {
  CategorySummary,
  Expense,
  ExpenseParams,
  ExpenseQuery,
  MonthSummary,
} from '@/types/expense'

/**
 * 消费接口，对应 docs/接口清单.md §9。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */

/**
 * 把空串归一成 undefined。
 *
 * axios 会**丢掉值为 undefined 的参数**，但会老老实实把 `startDate=` 发出去。
 * 两者在后端 trim 之后结果一样，可一旦看日志就会分不清"用户没筛"和
 * "用户筛了个空串" —— 排查时这种歧义要多花一次试探才能消掉。
 */
function orUndefined(value: string | null | undefined): string | undefined {
  const trimmed = value?.trim()
  return trimmed ? trimmed : undefined
}

/** 分页列表，可按日期区间与分类筛选（三个条件都可选） */
export function page(query: ExpenseQuery) {
  return http<PageResult<Expense>>({
    url: '/expense',
    method: 'get',
    params: {
      pageNum: query.pageNum,
      pageSize: query.pageSize,
      startDate: orUndefined(query.startDate),
      endDate: orUndefined(query.endDate),
      category: orUndefined(query.category),
    },
  })
}

/** 详情。列表项本身字段已经齐了，这个接口主要给"编辑前重取一次"用 */
export function detail(id: number) {
  return http<Expense>({ url: `/expense/${id}`, method: 'get' })
}

/** 新增。返回的是**回读**过的那条，两个时间戳和金额的小数位都以它为准 */
export function create(data: ExpenseParams) {
  return http<Expense>({ url: '/expense', method: 'post', data })
}

/** 全量替换。想清空备注就传空串，别指望"不传即保留" */
export function update(id: number, data: ExpenseParams) {
  return http<Expense>({ url: `/expense/${id}`, method: 'put', data })
}

export function remove(id: number) {
  return http<void>({ url: `/expense/${id}`, method: 'delete' })
}

/**
 * 按分类汇总。不传区间就是**全部**记录，不是"本月" ——
 * 默认区间是页面的事（见 ExpenseView 的 defaultRange），接口只认传进来的值。
 */
export function summaryByCategory(startDate?: string, endDate?: string) {
  return http<CategorySummary[]>({
    url: '/expense/summary/category',
    method: 'get',
    params: { startDate: orUndefined(startDate), endDate: orUndefined(endDate) },
  })
}

/** 按月汇总。不传 year 就是今年；返回**固定 12 条**（见 MonthSummary 的注释） */
export function summaryByMonth(year?: number) {
  return http<MonthSummary[]>({
    url: '/expense/summary/month',
    method: 'get',
    params: { year },
  })
}
