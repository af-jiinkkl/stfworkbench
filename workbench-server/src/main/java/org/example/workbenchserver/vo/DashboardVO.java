package org.example.workbenchserver.vo;

import java.math.BigDecimal;
import java.util.List;

/**
 * 首页聚合返回体，见 docs/接口清单.md §7。
 *
 * <p>这不是一个资源，是把首页要用的几份数据打成一个包。首页原本要发几个请求
 * （今日计划、即将到来的生日、备忘条数、今日消费），它们的 loading 状态互相独立，
 * 页面会先亮一块、再亮一块。聚合成一个请求后，要么整块有、要么整块没有。
 *
 * <p>注意 {@code upcomingAnniversaries} 复用的是
 * {@link UpcomingAnniversaryVO}，与 {@code GET /api/anniversary/upcoming}
 * **完完全全是同一个对象**。这里刻意不另建一个"首页专用"的类型：
 * 一旦有两份实现，"还有几天"就可能算出两个答案，而生日差一天没人会当成 bug 报上来。
 *
 * <p>每加一个模块就往这里加一个字段，是这个类型**预期内**的用法 ——
 * 首页的卡片有几个，聚合就返回几项。真正要守住的是另一条：新字段必须来自
 * 那个模块自己的 Service 方法，而不是在这里现拼一条查询。
 *
 * @param todayPlan              今日计划摘要
 * @param todayCourses           今天要上的课，按节次升序；今天不属于任何学期时是空列表
 * @param upcomingAnniversaries  提前提醒窗口内的生日/纪念日，按剩余天数升序
 * @param memoCount              备忘总条数，只用于卡片上显示一个数字
 * @param todayExpenseAmount     今日消费合计，恒非 null（没有记录时是 0.00）
 */
public record DashboardVO(

		TodayPlanVO todayPlan,

		/**
		 * 今天要上的课，复用的是 {@link CourseVO}，与课程表页拿到的是**同一个对象**。
		 *
		 * <p>与 {@code upcomingAnniversaries} 同一个理由：首页和课表页各建一个类型，
		 * "今天算第几周、这门课这周上不上"就可能有第二份判断 ——
		 * 而它的表现是"课表上显示有、首页说今天没课"，没人会当成 bug 报上来。
		 *
		 * <p>今天不属于任何学期（寒暑假、还没建学期）时是**空列表**，不是错误。
		 */
		List<CourseVO> todayCourses,

		List<UpcomingAnniversaryVO> upcomingAnniversaries,

		/** 已逻辑删除的不计入 */
		long memoCount,

		/**
		 * 今日消费合计。
		 *
		 * <p>用 {@code BigDecimal} 而不是 double —— 金额一律不许过浮点
		 * （见 CLAUDE.md 的数据库规范）。这里虽然是只读的展示值，
		 * 但只要它有一次以 double 的身份存在过，就迟早会有人拿它去做别的事。
		 */
		BigDecimal todayExpenseAmount) {

}
