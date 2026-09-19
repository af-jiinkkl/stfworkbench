package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.PlanTask;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 对外的任务结构，字段与 docs/接口清单.md §4 的示例一一对应。
 *
 * <p>白名单式 VO：{@code userId} / {@code deleted} / {@code createTime} /
 * {@code updateTime} 都**不在**这里面。这些字段前端用不上，返回出去只是
 * 多暴露一份内部信息；将来实体加了敏感字段，也不会自动流出去。
 */
public record PlanTaskVO(

		Long id,

		LocalDate planDate,

		String content,

		/** 0 未完成 / 1 已完成 */
		Integer completed,

		/** 未完成时为 null。接口清单要求这个字段显式输出 null 而不是被省略 */
		LocalDateTime completedTime,

		Integer sortOrder) {

	public static PlanTaskVO from(PlanTask task) {
		return new PlanTaskVO(
				task.getId(),
				task.getPlanDate(),
				task.getContent(),
				task.getCompleted(),
				task.getCompletedTime(),
				task.getSortOrder());
	}

}
