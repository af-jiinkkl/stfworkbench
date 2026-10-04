package org.example.workbenchserver.service;

import org.example.workbenchserver.dto.CourseDTO;
import org.example.workbenchserver.vo.CourseVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 课程业务，接口见 docs/接口清单.md §11。
 *
 * <p>全类不带 {@code userId} 参数：当前用户由 {@code UserContext} 提供，
 * 隔离由租户插件在 SQL 生成阶段完成。方法签名里出现 {@code userId}
 * 就等于给了调用方"传别人的 id"这个可能，而那正是要杜绝的。
 */
public interface CourseService {

	/**
	 * 查某学期的全部课程（课表数据），不分页 —— 一个学期的课就几十条。
	 *
	 * <p>返回顺序固定为 {@code dayOfWeek → startSection → id}：
	 * 前端拿到的就是可以直接铺进网格的顺序，不必自己再排一遍
	 * （再排一遍就是第二份"谁在前"的判断）。
	 *
	 * @throws org.example.workbenchserver.common.exception.BusinessException
	 *         {@code semesterId} 不存在**或不属于当前用户**时返回 404
	 */
	List<CourseVO> listBySemester(Long semesterId);

	/**
	 * 今天要上的课，按节次升序。首页的「今日课程」用它。
	 *
	 * <p>今天是第几周由后端算：要找得出**包含今天**的那个学期，
	 * 才能把"今天"换算成"第 N 周的第几天"。没有学期包含今天时返回空列表
	 * （寒暑假、或者还没建学期）—— 那是正常状态，不是错误。
	 *
	 * <p>单双周的判断也在这里做，用的是
	 * {@link org.example.workbenchserver.common.CourseTime#occursOnWeek} ——
	 * **不在前端再写一遍**。两处各判一次，迟早出现"课表上显示有、
	 * 首页说今天没课"这种没人会当成 bug 报上来的分叉。
	 *
	 * @param date 要查的日期，通常传 {@code WorkbenchTime.today()}
	 */
	List<CourseVO> listOnDate(LocalDate date);

	/**
	 * 新增。
	 *
	 * @throws org.example.workbenchserver.common.exception.BusinessException
	 *         {@code semesterId} 不是自己的返回 404；节次 / 星期 / 周次
	 *         越界或前后颠倒返回 400
	 */
	CourseVO create(CourseDTO dto);

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	CourseVO update(Long id, CourseDTO dto);

	void delete(Long id);

}
