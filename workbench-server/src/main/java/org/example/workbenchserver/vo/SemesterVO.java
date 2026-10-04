package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Semester;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期出参。
 *
 * <p>不含 {@code userId} —— 出参里出现它既没用（问的就是"我的"），
 * 又会把"数据按用户切分"这件事泄露成一个可观察的字段。
 *
 * <p>{@code startDate} 原样返回第 1 周的周一。**不在后端算成"今天第几周"**：
 * 那是相对"今天"的，而今天每天都在变。这个数字一旦进了响应体，
 * 就会被前端缓存下来，第二天显示的还是昨天算出来的周次。
 * 前端每次渲染时自己按今天的日期算，慢一步但永远是对的。
 *
 * @param id         主键
 * @param name       学期名称
 * @param startDate  第 1 周的周一
 * @param totalWeeks 总周数
 * @param createTime 创建时间
 * @param updateTime 最后修改时间
 */
public record SemesterVO(Long id, String name, LocalDate startDate, Integer totalWeeks,
		LocalDateTime createTime, LocalDateTime updateTime) {

	public static SemesterVO from(Semester semester) {
		return new SemesterVO(
				semester.getId(),
				semester.getName(),
				semester.getStartDate(),
				semester.getTotalWeeks(),
				semester.getCreateTime(),
				semester.getUpdateTime());
	}

}
