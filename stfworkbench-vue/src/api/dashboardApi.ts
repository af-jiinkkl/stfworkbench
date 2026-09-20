import { http } from '@/utils/request'
import type { Dashboard } from '@/types/dashboard'

/**
 * 首页聚合接口，对应 docs/接口清单.md §7。
 *
 * 没有参数：返回的永远是**当前登录用户**的那一份，"资源"就是登录态本身。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */
export function overview() {
  return http<Dashboard>({ url: '/dashboard', method: 'get' })
}
