package org.example.workbenchserver.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;

/**
 * 修改任务入参，对应 {@code PUT /api/plan-task/{id}}（docs/接口清单.md §4）。
 *
 * <p><b>为什么这里没有 {@code completed}？</b>
 * 因为切换完成状态有独立的 {@code PATCH /{id}/completed}。两者的语义和
 * 触发场景完全不同 —— 修改内容是低频的、用户在编辑框里敲完才提交；
 * 勾选是高频的、点一下就发。混在同一个接口里，编辑时的一次保存就可能
 * 顺手把完成状态覆盖掉。
 *
 * <p>{@code planDate} 与 {@code sortOrder} 可省略，省略即保持原值。
 */
public record PlanTaskUpdateDTO(

		@NotBlank(message = "不能为空")
		@Size(max = 255, message = "长度不能超过 255 个字符")
		String content,

		LocalDate planDate,

		Integer sortOrder) {

}
