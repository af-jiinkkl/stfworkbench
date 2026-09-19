package org.example.workbenchserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 新增任务入参，对应 {@code POST /api/plan-task}（docs/接口清单.md §4）。
 *
 * <p>校验消息写成"不能为空"而非"planDate 不能为空"：全局异常处理器会拼上
 * 字段名（见 {@code GlobalExceptionHandler}），消息里再写一遍就成了
 * "planDate planDate 不能为空"。
 *
 * <p><b>没有 {@code userId} 字段</b>：归属由服务端从 token 决定，客户端无权指定。
 */
public record PlanTaskCreateDTO(

		@NotNull(message = "不能为空")
		LocalDate planDate,

		@NotBlank(message = "不能为空")
		@Size(max = 255, message = "长度不能超过 255 个字符")
		String content,

		/** 排序值，可省略。省略时为 0，同值之间按 id 排，效果就是"追加到末尾" */
		Integer sortOrder) {

}
