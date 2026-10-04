package org.example.workbenchserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * 课程入参。新增与修改共用一份 —— 后端 PUT 是全量替换，没有"不给就不改"的字段。
 *
 * <p><b>这里只做"字段本身合不合法"的校验，范围校验一律在 Service 里。</b>
 * 两类东西这边管不了：
 *
 * <ul>
 *   <li>互相之间的约束：{@code startSection <= endSection}、
 *       {@code startWeek <= endWeek} —— 单字段注解表达不了</li>
 *   <li>跨表的约束：{@code endWeek} 不能超过**所属学期**的总周数，
 *       以及 {@code semesterId} 必须存在且属于自己 —— 都要先查库</li>
 * </ul>
 *
 * <p>{@code dayOfWeek} 这类取值范围的校验也放 Service：用
 * {@code @Min/@Max} 写一遍、Service 再写一遍，就是同一组数字有两个来源。
 * 统一走 {@link org.example.workbenchserver.common.CourseTime}，
 * 那里还带着"为什么是这些数字"的说明。
 *
 * <p>{@code teacher} / {@code location} 允许不填（空串），
 * 但不可为 null —— 理由同消费的 remark：MyBatis-Plus 的默认更新策略
 * 会把 null 字段整条跳过，PUT 清空时旧值会原样留在库里。
 */
public record CourseDTO(

		@NotNull(message = "不能为空")
		Long semesterId,

		@NotBlank(message = "不能为空")
		@Size(max = 50, message = "长度不能超过 50 个字")
		String name,

		@Size(max = 50, message = "长度不能超过 50 个字")
		String teacher,

		@Size(max = 50, message = "长度不能超过 50 个字")
		String location,

		@NotNull(message = "不能为空")
		Integer dayOfWeek,

		@NotNull(message = "不能为空")
		Integer startSection,

		@NotNull(message = "不能为空")
		Integer endSection,

		@NotNull(message = "不能为空")
		Integer startWeek,

		@NotNull(message = "不能为空")
		Integer endWeek,

		/** 0 每周 / 1 单周 / 2 双周。不传按每周处理 */
		Integer weekType

) {
}
