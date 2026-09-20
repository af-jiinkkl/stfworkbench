import { http } from '@/utils/request'
import type { PageResult } from '@/types/api'
import type { Memo, MemoDetail, MemoParams } from '@/types/memo'

/**
 * 备忘录接口，对应 docs/接口清单.md §6。
 *
 * 路径不含 `/api` 前缀 —— baseURL 里已经带了（见 utils/request.ts）。
 */

/**
 * 分页列表，可按关键词搜索。
 *
 * 关键词是**可选的**，为空时干脆不传这个参数：后端会 trim 后按"没搜"处理，
 * 但传一个 `keyword=` 空串过去，看日志的人会分不清"用户搜了空串"和"没搜"。
 */
export function page(pageNum: number, pageSize: number, keyword?: string) {
  return http<PageResult<Memo>>({
    url: '/memo',
    method: 'get',
    params: { pageNum, pageSize, keyword: keyword || undefined },
  })
}

/** 详情。列表没有正文，要看正文只能走这里 */
export function detail(id: number) {
  return http<MemoDetail>({ url: `/memo/${id}`, method: 'get' })
}

export function create(data: MemoParams) {
  return http<MemoDetail>({ url: '/memo', method: 'post', data })
}

/** 全量替换。想清空正文就传空串，别指望"不传即保留" */
export function update(id: number, data: MemoParams) {
  return http<MemoDetail>({ url: `/memo/${id}`, method: 'put', data })
}

export function remove(id: number) {
  return http<void>({ url: `/memo/${id}`, method: 'delete' })
}
