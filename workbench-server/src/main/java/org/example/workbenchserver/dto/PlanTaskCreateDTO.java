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

		/**
		 * 排序值，一般省略。省略时取当天最大值 + 1，也就是追加到末尾
		 * （见 {@code PlanTaskServiceImpl#nextSortOrder}）。
		 *
		 * <p>保留这个入参只是为了支持"插入到指定位置"这类批量导入场景；
		 * 界面上的新增与拖动排序都不传它。
		 */
		Integer sortOrder) {

}
