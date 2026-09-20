package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Anniversary;

/**
 * 对外的生日/纪念日结构，字段与 docs/接口清单.md §5 的示例一一对应。
 *
 * <p>白名单式 VO：{@code userId} / {@code deleted} / {@code createTime} /
 * {@code updateTime} 都**不在**这里面（CLAUDE.md 禁止直接返回 Entity）。
 *
 * <p>{@code nextDate} / {@code daysUntil} 不在这里，而在
 * {@link UpcomingAnniversaryVO} —— 它们只在"即将到来"这个场景下才有意义，
 * 普通列表里返回只会让前端以为可以自己算。接口清单 §5 明确要求前端不要自己算。
 */
public record AnniversaryVO(

		Long id,

		String name,

		/** 1 生日 / 2 纪念日 */
		Integer type,

		String relation,

		Integer month,

		Integer day,

		/** 提前提醒天数 1-7 */
		Integer remindDays,

		String remark) {

	public static AnniversaryVO from(Anniversary anniversary) {
		return new AnniversaryVO(
				anniversary.getId(),
				anniversary.getName(),
				anniversary.getType(),
				anniversary.getRelation(),
				anniversary.getMonth(),
				anniversary.getDay(),
				anniversary.getRemindDays(),
				anniversary.getRemark());
	}

}
