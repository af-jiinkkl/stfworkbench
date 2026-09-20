/**
 * 每日新闻。对应 docs/接口清单.md §8，与后端 NewsVO 一一对应。
 *
 * **这是全站唯一一份不按用户分的数据**：所有用户看到的是同一个列表，
 * 它由后端的定时任务抓取并缓存，不是谁私有的一条记录。
 * 所以这个模块没有"新建 / 编辑 / 删除"，也没有 user_id 这个概念 ——
 * 页面上不该出现任何"我的新闻"之类的说法。
 */

export interface News {
  id: number
  /** 标题。后端已 trim 过，并截断到 255 字 */
  title: string
  /**
   * 来源（第三方给的 author_name）。
   *
   * 第三方没给时是**空串**，不是 null —— 所以判空用 `v-if="item.source"` 就够了，
   * 不用写成 `!= null && !== ''`。
   */
  source: string
  /**
   * 原文链接。
   *
   * **可能是空串**（第三方偶尔不给 url）。此时这条新闻在界面上不可点，
   * 别拿它直接拼 `href` —— 空 href 会指向当前页，点一下像是页面刷新了。
   */
  url: string
  /**
   * 发布时间，`yyyy-MM-dd HH:mm:ss`。
   *
   * **可能为 null**：第三方的时间格式偶尔解析不了，那种记录仍然保留
   * （标题和链接都在，点开就能看），只是排序时排在最后 ——
   * 后端按 publishTime 倒序，null 落在末尾。
   */
  publishTime: string | null
}

/**
 * `2026-09-21 08:30:00` → `09-21 08:30`。**首页卡片和新闻页共用这一份**。
 *
 * 纯字符串切分，**不经过 Date**：后端给的格式由 docs/接口清单.md §1.5 定死，
 * 而 `new Date('2026-09-21 08:30:00')` 在各浏览器的解析行为并不一致
 * （见 utils/date.ts 开头那段）。这里只是把一串已经对的时间截短。
 *
 * 砍掉年份只留 `MM-DD`：新闻是当天那一批，年份是废话。
 * 保留到分钟、砍掉秒：秒对新闻没有意义。
 *
 * 两处各写一遍的话，同一门新闻在首页和新闻页会显示成两种格式 ——
 * 没人会当成 bug 报上来。这与 `sectionText` / `weekRangeText` 是同一个道理。
 */
export function timeText(publishTime: string): string {
  return publishTime.slice(5, 16)
}
