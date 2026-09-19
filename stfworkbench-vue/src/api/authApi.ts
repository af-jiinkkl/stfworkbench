import { http } from '@/utils/request'
import type { LoginParams, LoginResult, RegisterParams, UserInfo } from '@/types/user'

/**
 * 认证接口，对应 docs/接口清单.md §3。
 *
 * 注意路径不含 `/api` 前缀——baseURL 里已经带了。
 */

export function register(data: RegisterParams) {
  return http<void>({ url: '/auth/register', method: 'post', data })
}

export function login(data: LoginParams) {
  return http<LoginResult>({ url: '/auth/login', method: 'post', data })
}

export function getCurrentUser() {
  return http<UserInfo>({ url: '/auth/me', method: 'get' })
}
