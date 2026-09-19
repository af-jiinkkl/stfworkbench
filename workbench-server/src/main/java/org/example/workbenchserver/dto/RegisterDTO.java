package org.example.workbenchserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * 注册入参，校验规则见 docs/接口清单.md §3。
 *
 * <p>用 record：入参只读，且校验注解在 record 组件上能正常生效。
 *
 * <p>校验消息刻意不带字段名（如"用户名"），因为 GlobalExceptionHandler 会
 * 统一拼上字段名，写成 "username 长度需为 6-32 位"，避免消息里出现两遍。
 */
public record RegisterDTO(

		@NotBlank(message = "不能为空")
		@Pattern(regexp = "^[A-Za-z0-9_]{4,50}$", message = "只能由 4-50 位字母、数字或下划线组成")
		String username,

		@NotBlank(message = "不能为空")
		@Size(min = 6, max = 32, message = "长度需为 6-32 位")
		String password,

		/** 可空，为空时由 Service 默认取 username */
		@Size(max = 50, message = "长度不能超过 50 位")
		String nickname

) {
}
