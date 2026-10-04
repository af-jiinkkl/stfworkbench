import { http } from '@/utils/request'
import type { News } from '@/types/news'

/**
 * 每日新闻接口，对应 docs/接口清单.md §8。
 *
 * 整个模块只有**这一个**接口，因为新闻是只读的：它由后端的定时任务
 * （每小时一次）抓取并缓存进 `wb_news`，用户改不了它。
 * 所以没有新建 / 编辑 / 删除，也没有分页 —— 一天就几十条。
 *
 * 也**没有"立即刷新"**：抓取打的是聚合数据按次计费的接口，
 * 把它放在请求路径上，每个用户每刷一次页面就消耗一次配额，
 * 几十个用户就能把当天的额度耗光（§8 把这条定为架构决定，不是后期优化）。
 * 页面上那句"每小时自动更新一次"就是在说这件事。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */
export function listToday() {
  return http<News[]>({ url: '/news', method: 'get' })
}
