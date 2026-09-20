/**
 * 消费相关类型，字段与后端 ExpenseVO / CategorySummaryVO / MonthSummaryVO
 * 一一对应，见 docs/接口清单.md §9。
 */

/**
 * 预置分类。
 *
 * **这份清单在后端也有一份**（`ExpenseCategory` 枚举），两处必须一起改。
 * 这是刻意的重复：接口清单 §9 没有"取分类列表"这个端点，为一个只有六项、
 * 几乎不变、又要拿来做下拉选项的清单新增一个接口不划算。
 *
 * 不一致时的表现是**提交时撞上 400**（后端会把合法值列在错误提示里），
 * 而不是两边各自安好地跑下去 —— 这是选这个方案而不是"把清单塞进某个响应里
 * 做隐式同步"的原因：隐式同步一旦断掉，没有任何地方会报错。
 */
export const EXPENSE_CATEGORIES = ['餐饮', '交通', '购物', '娱乐', '医疗', '其他'] as const

/** 上面那份清单里的任一个 */
export type ExpenseCategory = (typeof EXPENSE_CATEGORIES)[number]

/**
 * 一条消费记录。
 *
 * `amount` 在 JSON 里是**数字**而不是字符串：后端的 BigDecimal 由 Jackson
 * 默认序列化成 JSON number。所以显示前一律走 `money()` 格式化，
 * 别直接把 `amount` 贴到界面上 —— `10.50` 到了 JS 里是 `10.5`，看着像掉了一位。
 */
export interface Expense {
  id: number
  amount: number
  category: string
  /** yyyy-MM-dd */
  expenseDate: string
  /** 可能为空串：后端把没填的备注落成空串而不是 null */
  remark: string
  /** yyyy-MM-dd HH:mm:ss */
  createTime: string
  updateTime: string
}

/**
 * 新增 / 修改的入参。
 *
 * 两者共用一份：后端的 PUT 是全量替换，字段集合与 POST 完全一致。
 * 备注想清空就传空串，不存在"不传即保留"。
 */
export interface ExpenseParams {
  amount: number
  category: string
  /** yyyy-MM-dd。注意这里是**消费发生的那天**，与创建时间无关，可以补录 */
  expenseDate: string
  remark: string
}

/**
 * 按分类汇总的一项（饼图）。
 *
 * 后端**不会**为没有记录的分类补 0 —— 补 0 会让饼图多出几块永远为 0 的扇区，
 * 图例被撑长，真正花过钱的分类反而挤在一起看不清。
 */
export interface CategorySummary {
  category: string
  amount: number
}

/**
 * 按月汇总的一项（折线图）。
 *
 * 与上面相反，这个是**补全的**：后端一定返回 12 条、按月份升序、
 * 没有记录的月份 amount 为 0。理由是折线图的横轴必须是完整的时间轴 ——
 * 只给有数据的月份，3 月直接连到 7 月，图上看着像"稳步增长"，
 * 实际中间四个月一分没花。补 0 是后端做的，前端不要再自己拼月份。
 */
export interface MonthSummary {
  /** yyyy-MM */
  month: string
  amount: number
}

/**
 * 列表查询条件。三个筛选都可选，为空时前端**不传这个参数**
 * （见 api/expenseApi.ts），后端按"没筛"处理。
 */
export interface ExpenseQuery {
  pageNum: number
  pageSize: number
  /** yyyy-MM-dd，闭区间 */
  startDate?: string
  endDate?: string
  category?: string
}
