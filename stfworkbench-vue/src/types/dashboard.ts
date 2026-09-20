import type { UpcomingAnniversary } from '@/types/anniversary'
import type { Course } from '@/types/course'
import type { PlanTask } from '@/types/plan'

/**
 * 首页聚合数据。字段与后端 DashboardVO 一一对应，见 docs/接口清单.md §7。
 *
 * 这个接口不是资源 CRUD，是把首页要用的几份数据打成一次响应 ——
 * 首页原本要发几个请求、维护几个 loading，页面会先亮一块再亮一块。
 *
 * **首页新增卡片时往这里加字段，而不是在页面里单独再发一个请求。**
 * 那样又会退回几个 loading 各亮各的，聚合就白做了。
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
   * 今天要上的课，已按节次升序。
   *
   * 与课程表页的 `Course` 是**同一个结构** —— "今天算第几周、
   * 这门课这周上不上"由后端算好（`CourseService#listOnDate`），
   * 前端不再自己判一遍。今天不属于任何学期（寒暑假、还没建学期）时是**空数组**。
   */
  todayCourses: Course[]
  /**
   * 提醒窗口内的生日/纪念日。与 `GET /api/anniversary/upcoming` 返回的是
   * **同一个结构**，所以能直接交给 UpcomingAnniversaryList 渲染 ——
   * "还有几天"的说法只有那一处实现。
   */
  upcomingAnniversaries: UpcomingAnniversary[]
  /** 备忘总条数，只用于卡片上显示一个数字 */
  memoCount: number
  /**
   * 今日消费合计，恒有值（没有记录时是 0）。
   *
   * 与 `amount` 一样是 JSON number —— 后端 BigDecimal 的默认序列化结果。
   * 显示前走 `money()` 格式化，别直接贴上去：`0` 会显示成"¥0"而不是"¥0.00"。
   */
  todayExpenseAmount: number
}
