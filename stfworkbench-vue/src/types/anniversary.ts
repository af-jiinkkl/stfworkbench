/**
 * 生日与纪念日相关类型。字段与后端 AnniversaryVO / UpcomingAnniversaryVO
 * 一一对应，见 docs/接口清单.md §5。
 */

/** 类型：1 生日 / 2 纪念日 */
export const TYPE_BIRTHDAY = 1
export const TYPE_MEMORIAL = 2

/** 常用关系。不限制用户只能选这几个 —— 表单里允许自己输入 */
export const RELATION_OPTIONS = ['自己', '家人', '朋友']

export interface Anniversary {
  id: number
  name: string
  type: number
  relation: string
  /** 月 1-12 */
  month: number
  /** 日 1-31（2 月可存 29，闰年生的人也要录得进来） */
  day: number
  /** 提前提醒天数 1-7 */
  remindDays: number
  remark: string
}

/**
 * 新增 / 修改的入参。
 *
 * 两者共用一份：后端的 PUT 是全量替换（字段集合与 POST 完全一致），
 * 不像每日计划那样有"不给就不改"的字段，所以不需要拆成两个类型。
 */
export interface AnniversaryParams {
  name: string
  type: number
  relation: string
  month: number
  day: number
  remindDays: number
  remark: string
}

/**
 * 即将到来的一条，供提醒用。
 *
 * 只有这个结构带 `daysUntil` / `nextDate` —— 它们**由后端算好**，
 * 前端直接用，不要自己拿 month/day 去推（见 docs/接口清单.md §5）。
 * 生日只存月日的代价就是跨年推算，那件事在后端做了一处、测了一处，
 * 前端再算一遍就多出一个可能对不上的口径。
 */
export interface UpcomingAnniversary {
  id: number
  name: string
  type: number
  relation: string
  month: number
  day: number
  /** 距下次还有几天，当天为 0 */
  daysUntil: number
  /** 下次落在哪天，yyyy-MM-dd */
  nextDate: string
}
