/**
 * 每日计划相关类型。字段与后端 PlanTaskVO 一一对应，见 docs/接口清单.md §4。
 */
export interface PlanTask {
  id: number
  /** yyyy-MM-dd */
  planDate: string
  content: string
  /**
   * 0 未完成 / 1 已完成。
   *
   * 刻意用 number 而不是 boolean：后端的接口契约就是数字（`"completed": 1`），
   * 前端自行转成布尔会让两边类型对不上，将来对照接口文档时反而要多绕一层。
   */
  completed: number
  /** yyyy-MM-dd HH:mm:ss；未完成时为 null（后端保证字段存在且为 null，不是 undefined） */
  completedTime: string | null
  sortOrder: number
}

export interface PlanTaskCreateParams {
  /** yyyy-MM-dd */
  planDate: string
  content: string
  /** 省略时后端按 0 处理，同值之间按 id 排，效果即"追加到末尾" */
  sortOrder?: number
}

/** 修改任务。completed 不在这里 —— 它走独立的 PATCH 接口 */
export interface PlanTaskUpdateParams {
  content: string
  planDate?: string
  sortOrder?: number
}
