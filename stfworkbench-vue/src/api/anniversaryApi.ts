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
 * 生日纪念日页和首页的提前提示（顶部"即将到来"）都调这一个。
 * docs/接口清单.md §7 规划了一个 `/api/dashboard` 聚合端点，
 * 落地后首页会改成只发那一次请求。
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
