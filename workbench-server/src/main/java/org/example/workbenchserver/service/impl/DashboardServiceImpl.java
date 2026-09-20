package org.example.workbenchserver.service.impl;

import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.service.AnniversaryService;
import org.example.workbenchserver.service.DashboardService;
import org.example.workbenchserver.service.ExpenseService;
import org.example.workbenchserver.service.MemoService;
import org.example.workbenchserver.service.PlanTaskService;
import org.example.workbenchserver.vo.DashboardVO;
import org.example.workbenchserver.vo.PlanTaskVO;
import org.example.workbenchserver.vo.TodayPlanVO;
import org.example.workbenchserver.vo.UpcomingAnniversaryVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 首页聚合实现。
 *
 * <p><b>本类一行 SQL 都没有，也不该有。</b>每个数字都来自其他 Service 的现成方法，
 * 这既是为了复用，也是为了"口径只有一处"：
 *
 * <ul>
 *   <li>今日计划走 {@link PlanTaskService#listByDate} 传 {@code null}（即"今天"），
 *       与每日计划页用的是同一个"今天"（{@code WorkbenchTime.today()}）</li>
 *   <li>即将到来的生日走 {@link AnniversaryService#upcoming()}，与纪念日页、
 *       以及原来的 {@code GET /api/anniversary/upcoming} 是同一段代码</li>
 *   <li>今日消费走 {@link ExpenseService#sumOf}，与消费页的饼图合计是同一条路径</li>
 * </ul>
 *
 * <p>若在这里自己拼一个 {@code LambdaQueryWrapper} 查表，就会有第二份
 * "哪些日子算即将到来"的判断。两份都能跑、都不会报错，只会在某一天悄悄给出
 * 不同的天数 —— 那是这类聚合接口最典型的坏法。
 *
 * <p>数据隔离同样不需要在这里操心：三个 Service 各自查的都是当前用户的数据，
 * 条件是拦截器注入的。这个类里没有 {@code user_id}，也不该有。
 */
@Service
public class DashboardServiceImpl implements DashboardService {

	private final PlanTaskService planTaskService;
	private final AnniversaryService anniversaryService;
	private final MemoService memoService;
	private final ExpenseService expenseService;

	public DashboardServiceImpl(PlanTaskService planTaskService,
			AnniversaryService anniversaryService,
			MemoService memoService,
			ExpenseService expenseService) {
		this.planTaskService = planTaskService;
		this.anniversaryService = anniversaryService;
		this.memoService = memoService;
		this.expenseService = expenseService;
	}

	@Override
	public DashboardVO overview() {
		// 几个查询串行发出。合并成一条 SQL 会牵进跨表的 union，
		// 可读性和可维护性都不划算 —— 几次单表查询在这个数据量下毫无压力，
		// 省下的是**前端那几次 HTTP 往返**，那才是聚合接口的意义所在
		List<PlanTaskVO> todayTasks = planTaskService.listByDate(null);

		// total / completed 由 TodayPlanVO.of 从列表本身推出来，
		// 不在这里单独数一遍 —— 数两遍就有对不上的可能
		TodayPlanVO todayPlan = TodayPlanVO.of(todayTasks);

		List<UpcomingAnniversaryVO> upcoming = anniversaryService.upcoming();

		// "今天"同样来自 WorkbenchTime，与今日计划用的是同一个口径。
		// 这里传 today..today 而不是让 ExpenseService 提供一个 todayTotal()：
		// 区间是调用方的事，服务层不需要知道"首页想看今天"这件事
		LocalDate today = WorkbenchTime.today();
		BigDecimal todayExpense = expenseService.sumOf(today, today);

		return new DashboardVO(todayPlan, upcoming, memoService.count(), todayExpense);
	}

}
