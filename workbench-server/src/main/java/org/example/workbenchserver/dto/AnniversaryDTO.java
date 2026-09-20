package org.example.workbenchserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 生日/纪念日入参，同时用于新增与修改（docs/接口清单.md §5）。
 *
 * <p><b>为什么新增和修改共用一个 DTO，而每日计划那边是两个</b>：
 * {@code PUT} 的语义是全量替换（CLAUDE.md 的 RESTful 约定），
 * 所以修改时每个字段都必须给 —— 字段集合与新增完全一致。
 * 每日计划的 PUT 里有 {@code planDate}/{@code sortOrder} 两个"不给就不改"的可选字段，
 * 那时才需要分开。这里若硬拆两个一模一样的类，只是多一份要同步维护的重复。
 *
 * <p><b>月/日的组合是否合法不在这里校验</b>：{@code day} 的上限随 {@code month} 变化
 * （4 月没有 31 日），而 Bean Validation 的注解是逐字段独立的，表达不了这种依赖。
 * 那部分由 {@code AnniversaryServiceImpl} 校验，见其 {@code validateMonthDay}。
 *
 * <p>校验消息写成"不能为空"而非"name 不能为空"：全局异常处理器会拼上字段名，
 * 消息里再写一遍会变成"name name 不能为空"。
 *
 * <p><b>没有 {@code userId} 字段</b>：归属由服务端从 token 决定，客户端无权指定。
 */
public record AnniversaryDTO(

		@NotBlank(message = "不能为空")
		@Size(max = 50, message = "长度不能超过 50 个字符")
		String name,

		/** 1 生日 / 2 纪念日 */
		@NotNull(message = "不能为空")
		@Min(value = 1, message = "只能是 1（生日）或 2（纪念日）")
		@Max(value = 2, message = "只能是 1（生日）或 2（纪念日）")
		Integer type,

		/** 关系：自己 / 家人 / 朋友。可省略 */
		@Size(max = 20, message = "长度不能超过 20 个字符")
		String relation,

		@NotNull(message = "不能为空")
		@Min(value = 1, message = "需在 1-12 之间")
		@Max(value = 12, message = "需在 1-12 之间")
		Integer month,

		/**
		 * 2 月允许填 29（闰年出生的人真实存在），因此这里上限放到 31，
		 * 具体该月有没有这一天交给 service 判断。
		 */
		@NotNull(message = "不能为空")
		@Min(value = 1, message = "需在 1-31 之间")
		@Max(value = 31, message = "需在 1-31 之间")
		Integer day,

		/** 提前提醒天数 1-7。可省略，省略时取 7（与建表默认值一致） */
		@Min(value = 1, message = "提前提醒天数需在 1-7 之间")
		@Max(value = 7, message = "提前提醒天数需在 1-7 之间")
		Integer remindDays,

		@Size(max = 255, message = "长度不能超过 255 个字符")
		String remark) {

}
