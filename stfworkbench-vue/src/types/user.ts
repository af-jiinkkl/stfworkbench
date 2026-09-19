/**
 * 用户信息。字段与后端 UserVO 一一对应，见 docs/接口清单.md §3。
 * 后端不会返回 password，这里也就不该有。
 */
export interface UserInfo {
  id: number
  username: string
  nickname: string
  avatar: string
}

export interface LoginParams {
  username: string
  password: string
}

export interface RegisterParams {
  username: string
  password: string
  /** 可空，后端为空时默认取 username */
  nickname?: string
}

export interface LoginResult {
  token: string
  userInfo: UserInfo
}
