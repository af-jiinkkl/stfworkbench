package org.example.workbenchserver.vo;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页「今日计划」摘要的推导。
 *
 * <p>纯单元测试，不连库、不启 Spring。
 *
 * <p>要钉的是一件事：<b>total 和 completed 永远是 tasks 的忠实派生</b>。
 * 首页卡片上写着"已完成 2 / 5"，分母来自 {@code total}、分子来自 {@code completed}，
 * 而下面列的条目来自 {@code tasks} —— 三个数一旦各算各的，
 * 就会出现"列表里 5 条、分母写着 4"这种自相矛盾，且不报任何错。
 * 由 {@code of()} 一处推导，这种矛盾在结构上就不可能发生，这条用例守的就是这个结构。
 */
class TodayPlanVOTest {

	private static PlanTaskVO task(long id, String content, int completed) {
		return new PlanTaskVO(
				id,
				LocalDate.of(2026, 9, 19),
				content,
				completed,
				completed == 1 ? LocalDateTime.of(2026, 9, 19, 10, 0) : null,
				0);
	}

	@Test
	@DisplayName("空列表 → 0 / 0，不是 null")
	void empty() {
		TodayPlanVO plan = TodayPlanVO.of(List.of());

		assertThat(plan.total()).isZero();
		assertThat(plan.completed()).isZero();
		assertThat(plan.tasks()).isEmpty();
	}

	@Test
	@DisplayName("一条都没完成")
	void noneCompleted() {
		TodayPlanVO plan = TodayPlanVO.of(List.of(
				task(1, "甲", 0),
				task(2, "乙", 0)));

		assertThat(plan.total()).isEqualTo(2);
		assertThat(plan.completed()).isZero();
	}

	@Test
	@DisplayName("全部完成")
	void allCompleted() {
		TodayPlanVO plan = TodayPlanVO.of(List.of(
				task(1, "甲", 1),
				task(2, "乙", 1)));

		assertThat(plan.total()).isEqualTo(2);
		assertThat(plan.completed()).isEqualTo(2);
	}

	/**
	 * 不变量式的用例：不管列表长什么样，两个数字都必须与列表对得上。
	 *
	 * <p>逐个边界值去试，覆盖的终究是"想得到的那些"；这一条扫的是一大片组合，
	 * 其中包括"我没想到的那几种"。派生逻辑一旦被改成由调用方传值，它会立刻红。
	 */
	@Test
	@DisplayName("任意组合下，total 恒等于列表长度、completed 恒等于未删除计数")
	void alwaysDerivesFromTasks() {
		for (int size = 0; size <= 12; size++) {
			for (int doneMask = 0; doneMask < (1 << Math.min(size, 8)); doneMask++) {
				List<PlanTaskVO> tasks = new ArrayList<>();
				for (int i = 0; i < size; i++) {
					tasks.add(task(i + 1, "任务 " + i, (doneMask >> i & 1)));
				}

				TodayPlanVO plan = TodayPlanVO.of(tasks);

				assertThat(plan.total()).isEqualTo(tasks.size());
				assertThat(plan.completed())
						.isEqualTo(tasks.stream().filter(t -> t.completed() == 1).count());
				assertThat(plan.tasks()).isSameAs(tasks);
			}
		}
	}

	/**
	 * {@code completed} 为 null 时不该抛 NPE。
	 *
	 * <p>建表脚本里这个列是 {@code NOT NULL}，所以这个分支正常永远走不到。
	 * 留着是因为实现里用了 {@code Integer.valueOf(1).equals(...)} 而不是
	 * {@code == 1}（后者拆箱时会炸），这条用例把那个选择固定下来：
	 * 真有脏数据溜进来，表现是"少算一个已完成"，而不是首页整个打不开。
	 */
	@Test
	@DisplayName("completed 为 null 时算作未完成，不抛异常")
	void nullCompletedCountsAsNotDone() {
		PlanTaskVO broken = new PlanTaskVO(
				9L, LocalDate.of(2026, 9, 19), "脏数据", null, null, 0);

		TodayPlanVO plan = TodayPlanVO.of(List.of(broken));

		assertThat(plan.total()).isEqualTo(1);
		assertThat(plan.completed()).isZero();
	}

}
