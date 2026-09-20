package org.example.workbenchserver.vo;

import java.util.List;

/**
 * 首页聚合返回体，见 docs/接口清单.md §7。
 *
 * <p>这不是一个资源，是把首页要用的三份数据打成一个包。首页原本要发三个请求
 * （今日计划、即将到来的生日、备忘条数），三个 loading 状态互相独立，
 * 页面会先亮一块、再亮一块。聚合成一个请求后，要么整块有、要么整块没有。
 *
 * <p>注意 {@code upcomingAnniversaries} 复用的是
 * {@link UpcomingAnniversaryVO}，与 {@code GET /api/anniversary/upcoming}
 * **完完全全是同一个对象**。这里刻意不另建一个"首页专用"的类型：
 * 一旦有两份实现，"还有几天"就可能算出两个答案，而生日差一天没人会当成 bug 报上来。
 *
 * @param todayPlan              今日计划摘要
 * @param upcomingAnniversaries  提前提醒窗口内的生日/纪念日，按剩余天数升序
 * @param memoCount              备忘总条数，只用于卡片上显示一个数字
 */
public record DashboardVO(

		TodayPlanVO todayPlan,

		List<UpcomingAnniversaryVO> upcomingAnniversaries,

		/** 已逻辑删除的不计入 */
		long memoCount) {

}
