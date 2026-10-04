package org.example.workbenchserver.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.example.workbenchserver.common.CourseTime;

import java.time.LocalDate;

/**
 * 学期入参。新增与修改共用一份 —— 后端 PUT 是全量替换，没有"不给就不改"的字段。
 *
 * <p><b>{@code startDate} 是第 1 周的周一</b>，这一点接口清单 §10 已经写明，
 * 但 {@code @NotNull} 只能保证它非空，管不了它是星期几。所以"是不是周一"
 * 放在 Service 里校验（见 {@code SemesterServiceImpl}）——
 * 那是业务规则，换一个入口（将来的导入）也得守。
 *
 * <p>{@code totalWeeks} 的范围来自 {@link CourseTime}，**不在这里再抄一遍数字** ——
 * 注解可以引用编译期常量，所以 {@code @Max(CourseTime.MAX_TOTAL_WEEKS)} 是合法的，
 * 而 Service 的第二道校验和错误提示用的是同一个常量。
 * 三处写死三个数字迟早会不一致，而不一致的表现是"某个合法值被拒"。
 *
 * <p>不夹取而报错 —— 把 200 悄悄夹成 60 会存下一个用户没要过的值，
 * 而他只有等课表翻不到头了才会发现。
 */
public record SemesterDTO(

		@NotBlank(message = "不能为空")
		@Size(max = 50, message = "长度不能超过 50 个字")
		String name,

		@NotNull(message = "不能为空")
		LocalDate startDate,

		@NotNull(message = "不能为空")
		@Min(value = CourseTime.MIN_TOTAL_WEEKS, message = "至少 " + CourseTime.MIN_TOTAL_WEEKS + " 周")
		@Max(value = CourseTime.MAX_TOTAL_WEEKS, message = "最多 " + CourseTime.MAX_TOTAL_WEEKS + " 周")
		Integer totalWeeks

) {
}
