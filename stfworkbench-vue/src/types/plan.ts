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
  /**
   * 一般省略。省略时后端取当天最大值 + 1，也就是追加到末尾。
   * 界面上新增不传它 —— 传了就等于绕过"一个写入口"的约定。
   */
  sortOrder?: number
}

/** 修改任务。completed 不在这里（走独立的 PATCH），sortOrder 也不在（走 /order） */
export interface PlanTaskUpdateParams {
  content: string
  planDate?: string
}

/**
 * 重排入参。`taskIds` 必须是当天任务的**完整**顺序 ——
 * 少传、多传、有重复后端一律 400（见 docs/接口清单.md §4）。
 */
export interface PlanTaskReorderParams {
  /** yyyy-MM-dd */
  planDate: string
  taskIds: number[]
}
