package org.example.workbenchserver.vo;

import java.util.List;

/**
 * 首页的「今日计划」摘要，见 docs/接口清单.md §7。
 *
 * <p>{@code total} 和 {@code completed} 都是 {@code tasks} 的派生值，
 * 但仍然单独给出来：首页的卡片上要显示"已完成 2 / 5"，
 * 前端为此把整个数组遍历一遍只为了数个数，没有必要。
 *
 * <p><b>派生值必须由 {@link #of} 一处算出来</b>，不能让调用方自己传。
 * 三个字段分开传的话，就有"tasks 里有 5 条、total 写着 4"这种自相矛盾的
 * 可能 —— 而这种错不会报错，只会让首页的分母悄悄不对。
 * 由同一个入参推导，矛盾在结构上就不可能发生。
 *
 * @param total     今日任务总数
 * @param completed 其中已完成的数量
 * @param tasks     今日任务，已按 sortOrder 排好（与 {@code GET /api/plan} 同源同序）
 */
public record TodayPlanVO(long total, long completed, List<PlanTaskVO> tasks) {

	public static TodayPlanVO of(List<PlanTaskVO> tasks) {
		// PlanTask.completed 是 0/1，不是 boolean。用 Integer.valueOf(1).equals(...)
		// 而不是 == 1：拆箱时若为 null 会抛 NPE，而这个字段建表时是 NOT NULL，
		// 真出现 null 说明数据层出了别的问题，不该在这里变成一个看不懂的空指针
		long done = tasks.stream()
				.filter(task -> Integer.valueOf(1).equals(task.completed()))
				.count();
		return new TodayPlanVO(tasks.size(), done, tasks);
	}

}
