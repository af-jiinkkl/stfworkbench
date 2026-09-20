package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Anniversary;

import java.time.LocalDate;

/**
 * 「即将到来」的生日/纪念日，供首页提醒使用（docs/接口清单.md §5）。
 *
 * <p>比 {@link AnniversaryVO} 多两个字段，且这两个**必须由后端算好**：
 *
 * <ul>
 *   <li>{@code daysUntil}：还有几天，当天为 {@code 0}</li>
 *   <li>{@code nextDate}：下一次落在哪天</li>
 * </ul>
 *
 * <p>不让前端自己算是因为跨年推算很容易写错：今天 12 月、生日在 1 月时，
 * 直接比较月和日会得出"已经过了 300 多天"这种结果。接口清单 §5 明确写了
 * "前端**不要自己算**，直接用这两个字段"。
 *
 * <p>不带 {@code remindDays}：筛选已经按它做完了，返回值里再给一遍，
 * 前端就可能拿去做二次判断，反而与这里的口径分叉。
 */
public record UpcomingAnniversaryVO(

		Long id,

		String name,

		/** 1 生日 / 2 纪念日 */
		Integer type,

		String relation,

		Integer month,

		Integer day,

		/** 距下一次还有几天，当天为 0 */
		long daysUntil,

		/** 下一次的日期，如 {@code 2026-10-05} */
		LocalDate nextDate) {

	/**
	 * @param daysUntil 由 {@code YearlyRecurrence} 算出，调用方保证与 {@code nextDate} 同源
	 */
	public static UpcomingAnniversaryVO of(Anniversary anniversary, long daysUntil, LocalDate nextDate) {
		return new UpcomingAnniversaryVO(
				anniversary.getId(),
				anniversary.getName(),
				anniversary.getType(),
				anniversary.getRelation(),
				anniversary.getMonth(),
				anniversary.getDay(),
				daysUntil,
				nextDate);
	}

}
