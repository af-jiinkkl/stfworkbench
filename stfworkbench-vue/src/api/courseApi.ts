import { http } from '@/utils/request'
import type { Course, CourseParams } from '@/types/course'

/**
 * 课程接口，对应 docs/接口清单.md §11。
 *
 * **没有 `today()`**：首页的「今日课程」走 `GET /api/dashboard` 聚合，
 * 不为它单独发一次请求（见 CLAUDE.md 里"首页三块数据来自一次请求"那条）。
 * 课表页要的是整学期的课，用 `listBySemester`。
 */

/**
 * 某学期的全部课程，已按 `dayOfWeek → startSection → id` 排好序，
 * 前端直接按这个顺序铺网格即可，**不要再排一遍**。
 */
export function listBySemester(semesterId: number) {
  return http<Course[]>({ url: '/course', method: 'get', params: { semesterId } })
}

/** 新增。返回的是**回读过**的那条 */
export function create(data: CourseParams) {
  return http<Course>({ url: '/course', method: 'post', data })
}

/** 全量替换。想清空老师或地点就传空串，别指望"不传即保留" */
export function update(id: number, data: CourseParams) {
  return http<Course>({ url: `/course/${id}`, method: 'put', data })
}

export function remove(id: number) {
  return http<void>({ url: `/course/${id}`, method: 'delete' })
}
