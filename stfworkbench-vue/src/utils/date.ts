/**
 * 日期工具。全站统一 `yyyy-MM-dd` 字符串（docs/接口清单.md §1.5）。
 *
 * 这里全部基于**本地时间**手工拼接，刻意不用 `Date.toISOString()`。
 * `toISOString()` 会先换算成 UTC —— 东八区的用户在早上 8 点之前调用它，
 * 拿到的日期是**前一天**。这个 bug 只在凌晨到早 8 点之间出现，
 * 白天怎么测都是对的，非常难查。
 */

function pad(n: number): string {
  return String(n).padStart(2, '0')
}

/** Date → `yyyy-MM-dd`（本地时间） */
export function toDateString(date: Date): string {
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}`
}

/**
 * `yyyy-MM-dd` → 本地时间的 Date。
 *
 * 不用 `new Date(str)`：按 ECMAScript 规范，纯日期字符串会被当作
 * **UTC 午夜**解析，在 UTC+8 下再取本地日期就又退了一天。
 * 走数字构造器则明确按本地时间解释。
 */
function fromDateString(dateStr: string): Date {
  // 拆包时给默认值：本函数可能在 isValidDateString 校验**之前**被调用，
  // 传进来一个残缺的字符串不该让整页崩掉。
  // 注意 ?? 只兜住 undefined，兜不住 NaN —— 而 NaN 正是我们想要的：
  // 它会让 Date 变成 Invalid Date，后续 isValidDateString 的比对自然不通过。
  const [year = 1970, month = 1, day = 1] = dateStr.split('-').map(Number)
  return new Date(year, month - 1, day)
}

/** 今天，`yyyy-MM-dd` */
export function today(): string {
  return toDateString(new Date())
}

/** 相对某天偏移若干天 */
export function addDays(dateStr: string, days: number): string {
  const date = fromDateString(dateStr)
  date.setDate(date.getDate() + days)
  return toDateString(date)
}

/**
 * 相对某天偏移若干月。
 *
 * 比 addDays 麻烦，是因为月末会溢出：1 月 31 日直接 `setMonth(+1)` 会得到
 * 3 月 3 日（2 月没有 31 号，多出来的天数顺延到下个月）。
 * 正确做法是先把"日"摘出来、把日期归到 1 号再挪月份，最后夹回原日或当月最后一天。
 */
export function addMonths(dateStr: string, months: number): string {
  const date = fromDateString(dateStr)
  const day = date.getDate()
  date.setDate(1)
  date.setMonth(date.getMonth() + months)
  const lastDay = new Date(date.getFullYear(), date.getMonth() + 1, 0).getDate()
  date.setDate(Math.min(day, lastDay))
  return toDateString(date)
}

/**
 * 这天是星期几，**1 是周一**、7 是周日。
 *
 * 与后端 `LocalDate.getDayOfWeek().getValue()` 同一套编号，也与课表的
 * `dayOfWeek` 字段同一套。刻意**不用** `Date.getDay()` ——
 * 它返回 0 是周日、1 是周一，两者在周日会差出一天，
 * 而周日恰好是课程表最右边那一列。
 */
export function weekdayOf(dateStr: string): number {
  const day = fromDateString(dateStr).getDay()
  return day === 0 ? 7 : day
}

/**
 * 从 `from` 到 `to` 相差几天 —— `to` 晚于 `from` 时为正，同一天为 0。
 *
 * 课表用它把"第 N 周的星期几"换算成真实日期：两者都先归到本地午夜，
 * 相减再除以一天的毫秒数。**除以 86400000 之后要 round** ——
 * 有夏令时的时区（中国没有，但这段代码不该假定）里某两天之间是 23 或 25 小时，
 * 直接取整会少算/多算一天，而那正是"课表整体偏一天"这类问题的来源。
 */
export function diffInDays(from: string, to: string): number {
  const start = fromDateString(from)
  const end = fromDateString(to)
  return Math.round((end.getTime() - start.getTime()) / 86400000)
}

/** `yyyy-MM-dd` 是否合法。用于兜住手工输入或异常数据 */
export function isValidDateString(value: string): boolean {
  if (!/^\d{4}-\d{2}-\d{2}$/.test(value)) {
    return false
  }
  // 再回写一遍比对，能挡掉 2026-02-30 这种格式对但不存在的日期
  return toDateString(fromDateString(value)) === value
}
