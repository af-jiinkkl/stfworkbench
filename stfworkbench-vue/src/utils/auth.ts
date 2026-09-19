const TOKEN_KEY = 'stfworkbench_token'

/**
 * token 存 localStorage 而不是内存：刷新页面后不用重新登录。
 *
 * 代价是 XSS 能读到它。当前版本接受这个取舍 —— 应用不渲染任何用户输入的 HTML，
 * 且后端是纯 API。将来若接入富文本内容，需要重新评估（改用 httpOnly Cookie
 * 或把 token 放内存 + refresh token 机制）。
 */
export function getToken(): string | null {
  return localStorage.getItem(TOKEN_KEY)
}

export function setToken(token: string): void {
  localStorage.setItem(TOKEN_KEY, token)
}

export function clearToken(): void {
  localStorage.removeItem(TOKEN_KEY)
}
