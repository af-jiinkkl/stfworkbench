import { defineStore } from 'pinia'
import { ref } from 'vue'
import * as authApi from '@/api/authApi'
import type { LoginParams, RegisterParams, UserInfo } from '@/types/user'
import { clearToken, getToken, setToken } from '@/utils/auth'

/**
 * 当前登录用户状态。
 *
 * token 的**唯一真源是 localStorage**，store 里的 token 只是它的响应式镜像 ——
 * 这样刷新页面时 store 重建也能从 localStorage 恢复。
 */
export const useUserStore = defineStore('user', () => {
  const token = ref<string | null>(getToken())
  const userInfo = ref<UserInfo | null>(null)

  async function login(params: LoginParams): Promise<void> {
    const result = await authApi.login(params)
    token.value = result.token
    userInfo.value = result.userInfo
    setToken(result.token)
  }

  async function register(params: RegisterParams): Promise<void> {
    await authApi.register(params)
  }

  /**
   * 拉取当前用户信息。
   *
   * 刷新页面后 store 里的 userInfo 是空的，但 token 还在 —— 用这个接口
   * 换取用户信息，同时等于向服务端验证了一次 token 是否仍然有效。
   */
  async function fetchCurrentUser(): Promise<void> {
    userInfo.value = await authApi.getCurrentUser()
  }

  function logout(): void {
    // 后端没有登出接口：token 是无状态的，前端丢掉即登出
    // （见 docs/接口清单.md §3 的说明）。
    token.value = null
    userInfo.value = null
    clearToken()
  }

  return { token, userInfo, login, register, fetchCurrentUser, logout }
})
