/**
 * 备忘录相关类型，与后端 MemoVO / MemoDetailVO 一一对应，
 * 见 docs/接口清单.md §6。
 */

/**
 * 列表项。**不含正文** —— 接口刻意只给一段摘要。
 *
 * 正文可能上万字，列表页一条也用不上，全查出来只是白白多传几 MB。
 * 要看正文走详情接口（{@link MemoDetail}）。
 */
export interface Memo {
  id: number
  /** 可能为空串：后端只要求"标题和正文不能都为空"，标题单独可以空 */
  title: string
  /** 正文压成一行后的前 60 个字，**由后端截好**，前端不要再截一次 */
  summary: string
  /** yyyy-MM-dd HH:mm:ss */
  updateTime: string
}

/** 详情。比列表项多出正文和创建时间 */
export interface MemoDetail {
  id: number
  title: string
  content: string
  createTime: string
  updateTime: string
}

/**
 * 新增 / 修改的入参。
 *
 * 两者共用一份：后端的 PUT 是全量替换，字段集合与 POST 完全一致。
 * 想清空就传空串 —— 后端把 null 和空值一律落成空串再写库
 * （见 MemoServiceImpl#apply），不存在"不传即保留"。
 */
export interface MemoParams {
  title: string
  content: string
}
