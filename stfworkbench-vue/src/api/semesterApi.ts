import { http } from '@/utils/request'
import type { Semester, SemesterParams } from '@/types/course'

/**
 * 学期接口，对应 docs/接口清单.md §10。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */

/** 学期列表，按开始日期倒序。不分页 —— 一个人手上就几个学期 */
export function list() {
  return http<Semester[]>({ url: '/semester', method: 'get' })
}

/** 新增。返回的是**回读过**的那条，两个时间戳以它为准 */
export function create(data: SemesterParams) {
  return http<Semester>({ url: '/semester', method: 'post', data })
}

/** 全量替换。改小总周数时若有课程的周次落到了范围外，后端返回 400 */
export function update(id: number, data: SemesterParams) {
  return http<Semester>({ url: `/semester/${id}`, method: 'put', data })
}

/** 该学期下还有课程时后端返回 400，不级联删 */
export function remove(id: number) {
  return http<void>({ url: `/semester/${id}`, method: 'delete' })
}
