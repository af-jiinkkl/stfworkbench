package org.example.workbenchserver.dto;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;

/**
 * 重排入参，对应 {@code PUT /api/plan-task/order}（docs/接口清单.md §4）。
 *
 * <p><b>{@code taskIds} 是当天任务的完整 id 列表，按新顺序排列。</b>
 *
 * <p><b>为什么收"整份顺序"而不是"把某一条挪到某处"</b>：顺序本来就是整个列表的
 * 属性。若只提交移动的那几条，没提交的会保留原值、可能插在中间，得到一个谁都没
 * 预期的顺序 —— 而且不报错。全量提交 + 服务端校验集合一致，对不上就当场报出来。
 *
 * <p>封顶 200 条：这个接口在服务端是"一次 UPDATE × N"，不设上限等于把 N 交给
 * 客户端定。一天的待办到不了这个数。
 */
public record PlanTaskOrderDTO(

		@NotNull(message = "不能为空")
		LocalDate planDate,

		@NotEmpty(message = "不能为空")
		@Size(max = 200, message = "一次最多重排 200 条")
		List<Long> taskIds) {

}
