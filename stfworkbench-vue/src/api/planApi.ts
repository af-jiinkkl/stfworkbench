import { http } from '@/utils/request'
import type { PlanTask, PlanTaskCreateParams, PlanTaskUpdateParams } from '@/types/plan'

/**
 * 每日计划接口，对应 docs/接口清单.md §4。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */

/** 查某天的任务。任务量很小，接口不分页 */
export function listByDate(date: string) {
  return http<PlanTask[]>({ url: '/plan-task', method: 'get', params: { date } })
}

/** 查日期区间内的任务。区间超过 6 个月后端会返回 400 */
export function listByRange(startDate: string, endDate: string) {
  return http<PlanTask[]>({
    url: '/plan-task/range',
    method: 'get',
    params: { startDate, endDate },
  })
}

export function createTask(data: PlanTaskCreateParams) {
  return http<PlanTask>({ url: '/plan-task', method: 'post', data })
}

/** 改内容 / 日期 / 排序。返回改后的完整任务对象 */
export function updateTask(id: number, data: PlanTaskUpdateParams) {
  return http<PlanTask>({ url: `/plan-task/${id}`, method: 'put', data })
}

/**
 * 切换完成状态。
 *
 * 用 PATCH 而非 PUT —— 只改一个字段（docs/接口清单.md §4）。
 * `completedTime` 由后端维护，前端不传。
 */
export function updateCompleted(id: number, completed: number) {
  return http<PlanTask>({
    url: `/plan-task/${id}/completed`,
    method: 'patch',
    data: { completed },
  })
}

export function removeTask(id: number) {
  return http<void>({ url: `/plan-task/${id}`, method: 'delete' })
}
