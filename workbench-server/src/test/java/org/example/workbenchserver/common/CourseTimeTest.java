package org.example.workbenchserver.common;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link CourseTime#occursOnWeek} 的纯单元测试 —— 不连库，毫秒级。
 *
 * <p>与 {@code YearlyRecurrenceTest} 是同一类：把一条**很容易写错、
 * 写错了却什么都不报**的判断逐个边界钉死。单双周判反了的表现是
 * "周三 1-2 节那门课，单周该上、课表上却空着" —— 用户只会以为自己记错了。
 *
 * <p>这里覆盖两种失败方式，它们各不相同：
 *
 * <ul>
 *   <li><b>周次范围</b>：第 3 周到第 9 周的课，在第 2 周和第 10 周上不上（都不上）</li>
 *   <li><b>单双周</b>：与范围**同时**生效，是取交集而不是覆盖 ——
 *       单周的课在第 4 周即使落在范围内也不上</li>
 * </ul>
 *
 * <p>最后一条用例拿一大片组合扫一遍，钉的是"任何一周的结果都只能是
 * 上或不上、不会抛异常"这个不变量。
 */
class CourseTimeTest {

	@Test
	@DisplayName("周次范围是闭区间，两端都算在内")
	void weekRangeIsInclusive() {
		// 第 3 到第 9 周的课
		assertThat(CourseTime.occursOnWeek(3, 9, CourseTime.WEEK_TYPE_EVERY, 2)).isFalse();
		assertThat(CourseTime.occursOnWeek(3, 9, CourseTime.WEEK_TYPE_EVERY, 3)).isTrue();
		assertThat(CourseTime.occursOnWeek(3, 9, CourseTime.WEEK_TYPE_EVERY, 9)).isTrue();
		assertThat(CourseTime.occursOnWeek(3, 9, CourseTime.WEEK_TYPE_EVERY, 10)).isFalse();
	}

	@Test
	@DisplayName("只上一周的课（startWeek == endWeek）只在那一天成立")
	void singleWeekCourse() {
		assertThat(CourseTime.occursOnWeek(5, 5, CourseTime.WEEK_TYPE_EVERY, 4)).isFalse();
		assertThat(CourseTime.occursOnWeek(5, 5, CourseTime.WEEK_TYPE_EVERY, 5)).isTrue();
		assertThat(CourseTime.occursOnWeek(5, 5, CourseTime.WEEK_TYPE_EVERY, 6)).isFalse();
	}

	@Test
	@DisplayName("单周课在偶数周不上，双周课在奇数周不上")
	void oddAndEvenWeeks() {
		// 第 1 到第 8 周、单周：1/3/5/7 上，2/4/6/8 不上
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_ODD, 1)).isTrue();
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_ODD, 2)).isFalse();
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_ODD, 3)).isTrue();
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_ODD, 8)).isFalse();

		// 同一条范围换成双周，结果**正好全部反过来** ——
		// 这两行是这条用例真正要钉的：判反了不会有任何报错
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_EVEN, 1)).isFalse();
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_EVEN, 2)).isTrue();
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_EVEN, 3)).isFalse();
		assertThat(CourseTime.occursOnWeek(1, 8, CourseTime.WEEK_TYPE_EVEN, 8)).isTrue();
	}

	@Test
	@DisplayName("奇数周的奇偶按『学期第几周』算，与日期无关")
	void parityIsRelativeToSemesterWeek() {
		// 第 1 周永远是"单周"，哪怕它在日历上是偶数周 —— 这条断言看着多余，
		// 但它把"用学期第几周"这个约定写进了测试。哪天有人改成传
		// 周历周数（WeekFields.weekOfYear），第 1 周就未必是单周了
		assertThat(CourseTime.occursOnWeek(1, 20, CourseTime.WEEK_TYPE_ODD, 1)).isTrue();
		assertThat(CourseTime.occursOnWeek(1, 20, CourseTime.WEEK_TYPE_EVEN, 1)).isFalse();
	}

	@Test
	@DisplayName("单双周与周次范围同时生效，取交集而非覆盖")
	void parityAndRangeCompose() {
		// 一个常见的写法错误是先判单双周再判范围，用 else 串起来 ——
		// 那样"第 4 周"会直接返回"单双周不匹配"，而范围那一关根本没被问到，
		// 结果看上去一样。真正区分得开的是**范围外**的周：
		assertThat(CourseTime.occursOnWeek(3, 6, CourseTime.WEEK_TYPE_EVERY, 7)).isFalse();
		assertThat(CourseTime.occursOnWeek(3, 6, CourseTime.WEEK_TYPE_ODD, 7)).isFalse();
		assertThat(CourseTime.occursOnWeek(3, 6, CourseTime.WEEK_TYPE_EVEN, 7)).isFalse();
		// 第 7 周是单周且在范围外，两种写法都得说"不上" ——
		// 所以这条用例保证的是"范围外恒为 false"，与单双周无关
	}

	@ParameterizedTest(name = "第 {0} 周：每周上={1}，单周上={2}，双周上={3}")
	@CsvSource({
			// week,  every,  odd,   even
			"1,  true,  true,  false",
			"2,  true,  false, true",
			"3,  true,  true,  false",
			"4,  true,  false, true",
			"5,  true,  true,  false",
			"6,  true,  false, true",
	})
	@DisplayName("第 1-6 周，三种周类型的结果逐周固定")
	void parityTable(int week, boolean every, boolean odd, boolean even) {
		assertThat(CourseTime.occursOnWeek(1, 6, CourseTime.WEEK_TYPE_EVERY, week)).isEqualTo(every);
		assertThat(CourseTime.occursOnWeek(1, 6, CourseTime.WEEK_TYPE_ODD, week)).isEqualTo(odd);
		assertThat(CourseTime.occursOnWeek(1, 6, CourseTime.WEEK_TYPE_EVEN, week)).isEqualTo(even);
	}

	/**
	 * 不变量式的用例：把周次扫描一大片，断言"单周集与双周集互补"。
	 *
	 * <p>它比逐条断言更耐改 —— 将来若有人调整周类型常量的含义，
	 * 只要"单双周互斥且覆盖全部周次"这件事还成立，这条就仍然通过；
	 * 而一旦出现"某一周单双周都上"或"都不上"，它会立刻红。
	 */
	@Test
	@DisplayName("单双周互斥且正好覆盖范围内的每一周")
	void oddAndEvenPartitionTheRange() {
		int startWeek = 3;
		int endWeek = 12;

		for (int week = 1; week <= 16; week++) {
			boolean odd = CourseTime.occursOnWeek(startWeek, endWeek, CourseTime.WEEK_TYPE_ODD, week);
			boolean even = CourseTime.occursOnWeek(startWeek, endWeek, CourseTime.WEEK_TYPE_EVEN, week);
			boolean inRange = week >= startWeek && week <= endWeek;

			assertThat(odd ^ even)
					.as("第 %d 周：单周=%s，双周=%s —— 两者必须恰好一个为真", week, odd, even)
					.isEqualTo(inRange);
		}
	}

	@Test
	@DisplayName("取值范围的判断与常量一致")
	void rangeChecks() {
		assertThat(CourseTime.isValidDayOfWeek(1)).isTrue();
		assertThat(CourseTime.isValidDayOfWeek(7)).isTrue();
		assertThat(CourseTime.isValidDayOfWeek(0)).isFalse();
		assertThat(CourseTime.isValidDayOfWeek(8)).isFalse();
		assertThat(CourseTime.isValidDayOfWeek(null)).isFalse();

		assertThat(CourseTime.isValidSection(1)).isTrue();
		assertThat(CourseTime.isValidSection(6)).isTrue();
		assertThat(CourseTime.isValidSection(7)).isFalse();
		assertThat(CourseTime.isValidSection(null)).isFalse();

		// 0/1/2 都合法：0 是"每周"，不是"没填"
		assertThat(CourseTime.isValidWeekType(0)).isTrue();
		assertThat(CourseTime.isValidWeekType(2)).isTrue();
		assertThat(CourseTime.isValidWeekType(3)).isFalse();
		assertThat(CourseTime.isValidWeekType(null)).isFalse();
	}

	@Test
	@DisplayName("周类型文案从常量推出，不会与常量脱节")
	void weekTypeLabels() {
		assertThat(CourseTime.weekTypeLabel(CourseTime.WEEK_TYPE_EVERY)).isEqualTo("每周");
		assertThat(CourseTime.weekTypeLabel(CourseTime.WEEK_TYPE_ODD)).isEqualTo("单周");
		assertThat(CourseTime.weekTypeLabel(CourseTime.WEEK_TYPE_EVEN)).isEqualTo("双周");
		assertThat(CourseTime.weekTypeLabel(null)).isEqualTo("未知");
		assertThat(CourseTime.weekTypeLabel(99)).isEqualTo("未知");
	}

}
