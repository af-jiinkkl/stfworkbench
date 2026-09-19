package org.example.workbenchserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * 切换完成状态入参，对应 {@code PATCH /api/plan-task/{id}/completed}
 * （docs/接口清单.md §4）。
 *
 * <p>用 {@code Integer} 而不是 {@code Boolean}，是为了和接口清单里
 * {@code {"completed": 1}} 的写法一致。{@code @Min}/{@code @Max} 把取值范围
 * 卡在 0/1：不卡的话前端传个 7 也会被写进库，之后"已完成"的判断
 * 到处都得写 {@code == 1}，很容易有人写成 {@code != 0}。
 */
public record PlanTaskCompletedDTO(

		@NotNull(message = "不能为空")
		@Min(value = 0, message = "只能是 0 或 1")
		@Max(value = 1, message = "只能是 0 或 1")
		Integer completed) {

}
