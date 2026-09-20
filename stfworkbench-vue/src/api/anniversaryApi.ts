import { http } from '@/utils/request'
import type { Anniversary, AnniversaryParams, UpcomingAnniversary } from '@/types/anniversary'

/**
 * 生日与纪念日接口，对应 docs/接口清单.md §5。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */

/** 全部记录。这个列表天然很小，接口不分页 */
export function list() {
  return http<Anniversary[]>({ url: '/anniversary', method: 'get' })
}

/**
 * 即将到来的记录（剩余天数 ≤ 各自的提醒天数）。
 *
 * 只有生日纪念日页在用。首页的提前提示走的是 `/api/dashboard` 聚合端点
 * （见 api/dashboardApi.ts）—— 那边返回的是**同一个结构**，
 * 后端两处调的是同一个 Service 方法，所以两边的"还有几天"不会分叉。
 */
export function upcoming() {
  return http<UpcomingAnniversary[]>({ url: '/anniversary/upcoming', method: 'get' })
}

export function create(data: AnniversaryParams) {
  return http<Anniversary>({ url: '/anniversary', method: 'post', data })
}

/** 全量替换。想清空 remark / relation 就传空串，别指望"不传即保留" */
export function update(id: number, data: AnniversaryParams) {
  return http<Anniversary>({ url: `/anniversary/${id}`, method: 'put', data })
}

export function remove(id: number) {
  return http<void>({ url: `/anniversary/${id}`, method: 'delete' })
}
