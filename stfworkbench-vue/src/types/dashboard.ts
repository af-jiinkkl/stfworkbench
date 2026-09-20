import type { UpcomingAnniversary } from '@/types/anniversary'
import type { PlanTask } from '@/types/plan'

/**
 * 首页聚合数据。字段与后端 DashboardVO 一一对应，见 docs/接口清单.md §7。
 *
 * 这个接口不是资源 CRUD，是把首页要用的三份数据打成一次响应 ——
 * 首页原本要发三个请求、维护三个 loading，页面会先亮一块再亮一块。
 */

/** 今日计划摘要。total / completed 是后端从 tasks 推出来的，这里直接用 */
export interface TodayPlan {
  /** 今日任务总数 */
  total: number
  /** 其中已完成的数量 */
  completed: number
  /** 今日任务，已按 sortOrder 排好（与每日计划页同源同序） */
  tasks: PlanTask[]
}

export interface Dashboard {
  todayPlan: TodayPlan
  /**
   * 提醒窗口内的生日/纪念日。与 `GET /api/anniversary/upcoming` 返回的是
   * **同一个结构**，所以能直接交给 UpcomingAnniversaryList 渲染 ——
   * "还有几天"的说法只有那一处实现。
   */
  upcomingAnniversaries: UpcomingAnniversary[]
  /** 备忘总条数，只用于卡片上显示一个数字 */
  memoCount: number
}
