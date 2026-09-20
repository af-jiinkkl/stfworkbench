package org.example.workbenchserver.common.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link YearlyRecurrence} 的纯单元测试。
 *
 * <p>不启 Spring、不连数据库 —— 这段逻辑只跟日期算术有关，
 * 起一个容器要好几秒，而这些用例加起来不到一毫秒。
 *
 * <p>为什么这个模块最需要测：生日只存月和日，"下一次是哪天"是**算出来**的，
 * 而算错的表现很隐蔽 —— 12 月看 1 月的生日若按月份大小比较，会得出
 * "已经过了 300 多天"这种结果，界面上不会报错，只是安静地不提醒。
 * 所以这里把每个边界都钉死。
 */
class YearlyRecurrenceTest {

	@Test
	@DisplayName("今年还没到 → 就是今年")
	void nextOccurrenceStaysInCurrentYearWhenNotYetPassed() {
		LocalDate from = LocalDate.of(2026, 9, 19);

		assertThat(YearlyRecurrence.nextOccurrence(10, 5, from))
				.isEqualTo(LocalDate.of(2026, 10, 5));
	}

	@Test
	@DisplayName("今年已经过了 → 推到明年")
	void nextOccurrenceRollsToNextYearWhenAlreadyPassed() {
		LocalDate from = LocalDate.of(2026, 9, 19);

		// 昨天刚过
		assertThat(YearlyRecurrence.nextOccurrence(9, 18, from))
				.isEqualTo(LocalDate.of(2027, 9, 18));
	}

	@Test
	@DisplayName("跨年：今天 12 月、生日在 1 月 → 差几天，而不是差三百多天")
	void nextOccurrenceHandlesYearBoundary() {
		LocalDate from = LocalDate.of(2026, 12, 30);

		assertThat(YearlyRecurrence.nextOccurrence(1, 5, from))
				.isEqualTo(LocalDate.of(2027, 1, 5));
		// 6 天，不是 "已过去 359 天"
		assertThat(YearlyRecurrence.daysUntil(1, 5, from)).isEqualTo(6);
	}

	@Test
	@DisplayName("当天就是纪念日 → 剩余 0 天，且不推到明年")
	void todayIsTheDay() {
		LocalDate from = LocalDate.of(2026, 9, 19);

		assertThat(YearlyRecurrence.nextOccurrence(9, 19, from)).isEqualTo(from);
		assertThat(YearlyRecurrence.daysUntil(9, 19, from)).isZero();
	}

	@Test
	@DisplayName("2 月 29 日遇闰年 → 就是 2 月 29 日")
	void feb29InLeapYearStaysOnFeb29() {
		// 2024 是闰年
		assertThat(YearlyRecurrence.nextOccurrence(2, 29, LocalDate.of(2024, 1, 1)))
				.isEqualTo(LocalDate.of(2024, 2, 29));
	}

	@Test
	@DisplayName("2 月 29 日遇平年 → 退到 2 月 28 日，而不是跳过这一年")
	void feb29InCommonYearFallsBackToFeb28() {
		// 2025 是平年，没有 2/29
		assertThat(YearlyRecurrence.nextOccurrence(2, 29, LocalDate.of(2025, 1, 1)))
				.isEqualTo(LocalDate.of(2025, 2, 28));
	}

	@Test
	@DisplayName("2 月 29 日生的人，平年的 2 月 28 日当天就该看到提醒")
	void feb29PersonIsRemindedOnFeb28OfCommonYear() {
		LocalDate from = LocalDate.of(2025, 2, 28);

		assertThat(YearlyRecurrence.nextOccurrence(2, 29, from)).isEqualTo(from);
		assertThat(YearlyRecurrence.daysUntil(2, 29, from)).isZero();
	}

	@Test
	@DisplayName("闰年刚过 2 月 29 日 → 下一次落在明年的平年，即 2 月 28 日")
	void feb29AfterLeapDayRollsToCommonYearFeb28() {
		// 2024-03-01 时，2024-02-29 已经过去
		assertThat(YearlyRecurrence.nextOccurrence(2, 29, LocalDate.of(2024, 3, 1)))
				.isEqualTo(LocalDate.of(2025, 2, 28));
	}

	@Test
	@DisplayName("小月 31 日不会算出不存在的日期")
	void dayBeyondMonthLengthClampsToMonthEnd() {
		// 4 月只有 30 天。这种数据本应在入库时就被 validateMonthDay 拦掉，
		// 这里确认万一漏进来了，算出来的是 4 月 30 日而不是 4 月 31 日
		assertThat(YearlyRecurrence.nextOccurrence(4, 31, LocalDate.of(2026, 1, 1)))
				.isEqualTo(LocalDate.of(2026, 4, 30));
	}

	@Test
	@DisplayName("无论什么输入，下一次都不会落在过去")
	void nextOccurrenceIsNeverInThePast() {
		// 这条是"不变量"式的断言：上面那些具体用例各钉一个点，
		// 这条扫一大片，防止某个没想到的组合算出过去的日期
		List<LocalDate> fromDates = List.of(
				LocalDate.of(2024, 2, 28),
				LocalDate.of(2024, 2, 29),
				LocalDate.of(2025, 2, 28),
				LocalDate.of(2026, 12, 31),
				LocalDate.of(2027, 1, 1));

		for (LocalDate from : fromDates) {
			for (int month = 1; month <= 12; month++) {
				for (int day = 1; day <= 31; day++) {
					LocalDate next = YearlyRecurrence.nextOccurrence(month, day, from);
					assertThat(next)
							.as("%d-%d 在 %s 的下一次", month, day, from)
							.isAfterOrEqualTo(from);
					assertThat(YearlyRecurrence.daysUntil(month, day, from))
							.as("%d-%d 距 %s 的天数", month, day, from)
							.isNotNegative();
				}
			}
		}
	}

	/**
	 * 两个方法必须是同一组入参的纯函数，不能各算各的。
	 *
	 * <p>Service 里同时用了 {@code daysUntil} 和 {@code nextOccurrence}
	 * （前者填剩余天数、后者填下次日期）。若哪天有人"优化"掉其中一个的内部实现，
	 * 两者就可能给出互相矛盾的结果 —— 界面上会显示"还有 3 天"却标着下个月的日期。
	 */
	@Test
	@DisplayName("剩余天数与下次日期始终同源")
	void daysUntilAgreesWithNextOccurrence() {
		List<LocalDate> fromDates = List.of(
				LocalDate.of(2024, 2, 28),
				LocalDate.of(2025, 3, 1),
				LocalDate.of(2026, 9, 19),
				LocalDate.of(2026, 12, 30));

		for (LocalDate from : fromDates) {
			for (int month = 1; month <= 12; month++) {
				for (int day = 1; day <= 28; day++) {
					LocalDate next = YearlyRecurrence.nextOccurrence(month, day, from);
					assertThat(YearlyRecurrence.daysUntil(month, day, from))
							.as("%d-%d 距 %s", month, day, from)
							.isEqualTo(ChronoUnit.DAYS.between(from, next));
				}
			}
		}
	}

}
