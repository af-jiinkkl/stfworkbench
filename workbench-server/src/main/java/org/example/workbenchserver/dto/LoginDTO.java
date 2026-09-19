package org.example.workbenchserver.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * 登录入参。
 *
 * <p>这里**不做长度/格式校验**，只校验非空。因为对登录接口而言，
 * 详细校验既无意义（老用户的历史密码规则可能不同）又会泄露信息 ——
 * 若返回"用户名格式不对"，等于告诉攻击者这个账号不存在，
 * 从而可以枚举出哪些用户名是有效的。
 */
public record LoginDTO(

		@NotBlank(message = "不能为空")
		String username,

		@NotBlank(message = "不能为空")
		String password

) {
}
