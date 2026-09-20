package org.example.workbenchserver.common.util;

import java.time.LocalDate;
import java.time.Month;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;

/**
 * 「每年重复的月-日」推算：求下一次落在哪天、距今还有几天。
 *
 * <p>生日与纪念日只存月和日、不存年份（docs/数据模型.md §4.3），
 * 于是"下一次是哪天"必须自己算，且在 SQL 里做不了 —— 今天是 12 月 30 日、
 * 生日在 1 月 5 日这种跨年情形，比较月日大小会得出错误答案。
 *
 * <p>独立成类而不是塞进 Service 的私有方法，是为了**能单独测**：
 * 这段逻辑不碰数据库也不需要 Spring，直接以纯函数验证比启动整个容器快得多，
 * 也更容易把边界case（闰年、当天、跨年）逐个钉死。
 */
public final class YearlyRecurrence {

	private YearlyRecurrence() {
	}

	/**
	 * 求 {@code from} 当天或之后、最近的一次该纪念日。
	 *
	 * <p>当天即纪念日时返回 {@code from} 本身（"今天生日"要显示出来，
	 * 而不是推到明年）。
	 *
	 * <p><b>2 月 29 日的处理</b>：生日存 2/29 是合法的（闰年出生），但目标年份
	 * 可能不是闰年，那天根本不存在。这里**退到该年 2 月的最后一天**（平年即 2/28）。
	 * 另一种常见做法是顺延到 3/1，两者都说得通；选退不选进，是因为"提前想到"
	 * 比"事后想起"更符合提醒这个场景的用途。
	 *
	 * @param month 月 1-12
	 * @param day   日 1-31（该月不存在 29/30/31 时按上面的规则退到月末）
	 * @param from  起算日
	 */
	public static LocalDate nextOccurrence(int month, int day, LocalDate from) {
		LocalDate thisYear = clampToMonth(from.getYear(), month, day);
		if (!thisYear.isBefore(from)) {
			return thisYear;
		}
		return clampToMonth(from.getYear() + 1, month, day);
	}

	/**
	 * 距下一次还有几天。当天为 {@code 0}，不会是负数。
	 *
	 * <p>同时需要日期和天数时，把本方法与 {@link #nextOccurrence} 各调一次即可：
	 * 两者都是同一组入参的纯函数，内部算的是同一个日期，不会给出互相矛盾的结果。
	 *
	 * <p>用 {@link ChronoUnit#DAYS} 而不是自己减 {@code toEpochDay}：
	 * 前者语义明确，也不会在夏令时切换的地区差一天（本项目的业务时区没有夏令时，
	 * 但不该依赖这个巧合）。
	 */
	public static long daysUntil(int month, int day, LocalDate from) {
		return ChronoUnit.DAYS.between(from, nextOccurrence(month, day, from));
	}

	/**
	 * 构造该年该月该日的日期；若该日在该月不存在，退到当月最后一天。
	 *
	 * <p>月份本身非法（如 13 月）会在这里抛异常，调用方应在更早的校验里拦掉 ——
	 * 到这里仍非法说明是没校验住，宁可抛也不要静默算出个错日期。
	 */
	private static LocalDate clampToMonth(int year, int month, int day) {
		int lastDay = YearMonth.of(year, Month.of(month)).lengthOfMonth();
		return LocalDate.of(year, month, Math.min(day, lastDay));
	}

}
