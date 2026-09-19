package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.User;

/**
 * 对外暴露的用户信息，结构见 docs/接口清单.md §3。
 *
 * <p><b>刻意不含 password</b>：CLAUDE.md 规定禁止直接把 Entity 返回前端。
 * 用 VO 而不是给 Entity 加 {@code @JsonIgnore}，是因为后者的安全性取决于
 * "有没有人记得加注解"，而 VO 是白名单 —— 没写进去的字段不可能泄露出去。
 * 实体将来新增敏感字段时，也不会自动流出。
 */
public record UserVO(Long id, String username, String nickname, String avatar) {

	public static UserVO from(User user) {
		return new UserVO(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar());
	}

}
