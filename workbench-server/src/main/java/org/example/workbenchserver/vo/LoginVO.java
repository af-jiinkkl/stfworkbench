package org.example.workbenchserver.vo;

/**
 * 登录出参，结构见 docs/接口清单.md §3。
 *
 * @param token    后续请求放进 {@code Authorization: Bearer <token>}
 * @param userInfo 当前用户信息，前端存下来直接渲染，省一次 /api/auth/me 请求
 */
public record LoginVO(String token, UserVO userInfo) {
}
