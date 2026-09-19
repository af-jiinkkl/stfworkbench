import axios, { AxiosError, type AxiosRequestConfig } from 'axios'
import { ElMessage } from 'element-plus'
import type { ApiResult } from '@/types/api'
import { clearToken, getToken } from '@/utils/auth'

/**
 * axios 实例。
 *
 * baseURL 用 `/api` 相对路径，不写死后端地址：
 * 开发时由 vite.config.ts 的 server.proxy 转发到 8080，
 * 生产环境由 Nginx 把 `/api` 转发给后端。两边都不需要改前端代码，
 * 也顺带避开了跨域（浏览器眼里始终是同源请求）。
 */
const instance = axios.create({
  baseURL: '/api',
  timeout: 10000,
})

/**
 * 请求拦截器统一注入 token。
 * CLAUDE.md 明确要求"禁止每个接口手写"——写 30 遍就有 1 遍会漏。
 */
instance.interceptors.request.use((config) => {
  const token = getToken()
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

/**
 * 错误处理。后端约定 HTTP 状态码与 body 里的 code 一致（见 GlobalExceptionHandler），
 * 所以非 2xx 会走这里，错误信息可以从 error.response.data.msg 拿到。
 */
instance.interceptors.response.use(
  (response) => response,
  (error: AxiosError<ApiResult>) => {
    const status = error.response?.status
    const msg = error.response?.data?.msg

    if (status === 401) {
      // 这里刻意不 import router —— router 依赖 store，store 依赖 api，
      // api 依赖本文件，引入 router 会形成循环依赖。
      // 用整页跳转代替：顺带把 Pinia 里的内存状态也清干净了。
      clearToken()
      ElMessage.error(msg ?? '登录已过期，请重新登录')
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    else if (msg) {
      ElMessage.error(msg)
    }
    else {
      ElMessage.error('网络异常，请稍后重试')
    }

    return Promise.reject(error)
  },
)

/**
 * 统一请求方法：拆掉 `{code,msg,data}` 外壳，直接返回 data。
 *
 * 没有在响应拦截器里直接返回 `response.data.data`，是因为那样会让
 * axios 方法的静态类型与实际返回值对不上（TS 上要靠类型断言硬掰）。
 * 换成这个显式函数，调用方拿到的 T 就是真的 T。
 */
export async function http<T>(config: AxiosRequestConfig): Promise<T> {
  const response = await instance.request<ApiResult<T>>(config)
  const body = response.data

  // 正常情况下非 200 已经在上面被拦截了，这里兜底的是
  // "HTTP 200 但业务码非 200" 的情况（后端目前不会这么返回，防的是将来改约定）。
  if (body.code !== 200) {
    ElMessage.error(body.msg)
    throw new Error(body.msg)
  }

  return body.data
}
