/**
 * 课程表相关类型，字段与后端 SemesterVO / CourseVO 一一对应，
 * 见 docs/接口清单.md §10、§11。
 */

/** 一个学期。列表按开始日期倒序返回 */
export interface Semester {
  id: number
  name: string
  /** 第 1 周的**周一**，`yyyy-MM-dd`。后端拒绝非周一的日期 */
  startDate: string
  totalWeeks: number
  /** yyyy-MM-dd HH:mm:ss */
  createTime: string
  updateTime: string
}

/** 新增 / 修改学期的入参。后端 PUT 是全量替换，两者字段一致 */
export interface SemesterParams {
  name: string
  /** 必须是周一 */
  startDate: string
  totalWeeks: number
}

/**
 * 一门课在课表上占的格子。
 *
 * 一门课占多个格子（周一 1-2 节、周三 3-4 节）时，后端存的是**多条记录** ——
 * 所以这里没有"多时段"字段，每一格都是一个可以单独移动的独立对象。
 */
export interface Course {
  id: number
  semesterId: number
  name: string
  /** 可能为空串（后端把没填的落成空串而不是 null） */
  teacher: string
  /** 可能为空串 */
  location: string
  /** 星期几 1-7，**1 是周一** */
  dayOfWeek: number
  /** 开始节次 1-6 */
  startSection: number
  /** 结束节次 1-6，不小于 startSection */
  endSection: number
  /** 起始周，从 1 开始 */
  startWeek: number
  /** 结束周，不超过所属学期的总周数 */
  endWeek: number
  /** 0 每周 / 1 单周 / 2 双周 */
  weekType: number
}

/** 新增 / 修改课程的入参 */
export interface CourseParams {
  semesterId: number
  name: string
  teacher: string
  location: string
  dayOfWeek: number
  startSection: number
  endSection: number
  startWeek: number
  endWeek: number
  weekType: number
}

/** 星期几 1-7，下标即 dayOfWeek - 1 */
export const DAYS_OF_WEEK = ['周一', '周二', '周三', '周四', '周五', '周六', '周日'] as const

// ---------- 节次与时段 ----------
//
// 一天 **6 节**。时段（早晨/上午/下午/晚上）**不存库**，由节次推导 ——
// 存一份就是给"上午"这个说法留了两个可能不一致的定义。
//
// 这份划分与后端 CourseTime 的 MIN_SECTION / MAX_SECTION 是同一件事，
// 跨语言没法引用，只能照抄一份（同 EXPENSE_CATEGORIES 的理由）。
// 不一致时的表现是"某一节选不出来"，会当场发现。

export const MIN_SECTION = 1
export const MAX_SECTION = 6

/** 时段划分。`from` / `to` 都是闭区间 */
export const SECTION_BANDS = [
  { label: '早晨', from: 1, to: 1 },
  { label: '上午', from: 2, to: 3 },
  { label: '下午', from: 4, to: 5 },
  { label: '晚上', from: 6, to: 6 },
] as const

/** 全部节次，下拉框用 */
export const SECTIONS = Array.from(
  { length: MAX_SECTION - MIN_SECTION + 1 },
  (_, index) => index + MIN_SECTION,
)

// ---------- 周类型 ----------

export const WEEK_TYPE_EVERY = 0
export const WEEK_TYPE_ODD = 1
export const WEEK_TYPE_EVEN = 2

export const WEEK_TYPE_OPTIONS = [
  { value: WEEK_TYPE_EVERY, label: '每周' },
  { value: WEEK_TYPE_ODD, label: '单周' },
  { value: WEEK_TYPE_EVEN, label: '双周' },
] as const

export function weekTypeLabel(weekType: number): string {
  return WEEK_TYPE_OPTIONS.find(option => option.value === weekType)?.label ?? '每周'
}

/**
 * 节次的说法：`第 3 节` / `第 3-4 节`。
 *
 * 和 `money()` 是同一类东西：一个说法只许有一处实现。课表页的课块、
 * 首页的今日课程都要显示它，各写一遍的话，"第 3-4 节"和"第 3、4 节"
 * 迟早会在两个页面上不一致 —— 而这种不一致没人会当成 bug 报上来。
 */
export function sectionText(startSection: number, endSection: number): string {
  return startSection === endSection
    ? `第 ${startSection} 节`
    : `第 ${startSection}-${endSection} 节`
}

/** 周次的说法：`第 5 周` / `第 1-18 周`。理由同上 */
export function weekRangeText(startWeek: number, endWeek: number): string {
  return startWeek === endWeek
    ? `第 ${startWeek} 周`
    : `第 ${startWeek}-${endWeek} 周`
}

/**
 * 这门课在第 `week` 周上不上。`week` 是**学期的第几周**，从 1 开始。
 *
 * 这是后端 `CourseTime#occursOnWeek` 的镜像，前端**必须**有这一份：
 * 课表页会把用户翻到任意一周（第 7 周、第 15 周），后端只有一个
 * `listOnDate` 管"今天"，管不到"第 7 周的周三"。
 *
 * **翻周时判断的就是这一条，不是拿日期去问后端。**两处判反的表现是
 * "单周的课在第 4 周显示出来" —— 界面不报错，只是那门课不该在。
 * 所以要改就两边一起改（后端那份在 `common/CourseTime.java`，
 * 首页的"今日课程"走的正是它）。
 */
export function occursOnWeek(course: Course, week: number): boolean {
  if (week < course.startWeek || week > course.endWeek) {
    return false
  }
  if (course.weekType === WEEK_TYPE_EVERY) {
    return true
  }
  const isOddWeek = week % 2 === 1
  return course.weekType === WEEK_TYPE_ODD ? isOddWeek : !isOddWeek
}
