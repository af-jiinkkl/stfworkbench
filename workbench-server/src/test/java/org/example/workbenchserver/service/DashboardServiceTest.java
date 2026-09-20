package org.example.workbenchserver.service;

import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.security.UserContext;
import org.example.workbenchserver.vo.DashboardVO;
import org.example.workbenchserver.vo.PlanTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页聚合接口的数据来源与隔离。
 *
 * <p>这个接口是全仓库唯一一个**一次返回三张表**的地方，所以两类风险都集中在这里：
 *
 * <ol>
 *   <li><b>隔离</b>：三份数据来自三张表，任何一处拦截器失效，
 *       首页上就会冒出别人的东西。其中 {@code memoCount} 最危险 ——
 *       它底层是 {@code selectCount(null)}，代码里一个谓词都没有，
 *       WHERE 完全由拦截器拼出来（见 {@code MemoServiceImpl#count}），
 *       失效时不是一个字段错，而是直接变成全表 COUNT</li>
 *   <li><b>自洽</b>：{@code total} / {@code completed} / {@code tasks} 三个数
 *       必须对得上，且只统计"今天"。这类错不抛异常，只是首页的分母悄悄不对</li>
 * </ol>
 *
 * <p>数据用原生 {@code JdbcTemplate} 带显式 {@code user_id} 种入，绕开 MyBatis ——
 * 理由同 {@code PlanTaskIsolationTest}：若改用 Mapper 插，拦截器一失效就会在
 * **插入**阶段抛 NOT NULL，测试红了却红在错误位置，真正的断言根本没跑过。
 *
 * <p>需要真实 MySQL，且三张表都已建好（见 db/schema.sql）。
 */
@SpringBootTest
class DashboardServiceTest {

	/** 合成用户 id，远离真实用户规模，可按 user_id 精确清理 */
	private static final long USER_A = 3020L;
	private static final long USER_B = 3021L;
	private static final long USER_C = 3022L;

	@Autowired
	private DashboardService dashboardService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUsers() {
		for (String table : new String[] {"wb_plan_task", "wb_anniversary", "wb_memo"}) {
			jdbcTemplate.update(
					"DELETE FROM `" + table + "` WHERE `user_id` IN (?, ?, ?)",
					USER_A, USER_B, USER_C);
		}
		UserContext.clear();
	}

	// ---------- 种数据的帮手 ----------

	private void insertTask(long userId, LocalDate date, String content, int completed) {
		jdbcTemplate.update(
				"INSERT INTO `wb_plan_task` (`user_id`, `plan_date`, `content`, `completed`, `sort_order`) "
						+ "VALUES (?, ?, ?, ?, 0)",
				userId, date, content, completed);
	}

	private void insertAnniversary(long userId, String name, LocalDate date) {
		jdbcTemplate.update(
				"INSERT INTO `wb_anniversary` (`user_id`, `name`, `type`, `relation`, `month`, `day`, `remind_days`, `remark`) "
						+ "VALUES (?, ?, 1, '', ?, ?, 1, '')",
				userId, name, date.getMonthValue(), date.getDayOfMonth());
	}

	private void insertMemo(long userId, String title) {
		jdbcTemplate.update(
				"INSERT INTO `wb_memo` (`user_id`, `title`, `content`) VALUES (?, ?, '')",
				userId, title);
	}

	// ---------- 用例 ----------

	/**
	 * 三个数字都只反映当前用户。
	 *
	 * <p>B 那边刻意种了更多数据（任务和备忘都比 A 多），
	 * 这样"漏进来了"与"算错了"是两种不同的失败：若隔离失效，
	 * 数字会变成 A+B 的合计，一眼可辨。
	 */
	@Test
	@DisplayName("三份数据都只含当前用户的，不含别人的")
	void onlyCountsOwnData() {
		LocalDate today = WorkbenchTime.today();

		insertTask(USER_A, today, "A 的甲", 1);
		insertTask(USER_A, today, "A 的乙", 0);
		insertTask(USER_B, today, "B 的丙", 1);
		insertTask(USER_B, today, "B 的丁", 1);
		insertTask(USER_B, today, "B 的戊", 0);

		insertAnniversary(USER_A, "A 的生日", today);
		insertAnniversary(USER_B, "B 的生日", today);

		insertMemo(USER_A, "A 的备忘");

		UserContext.set(USER_A);
		DashboardVO overview = dashboardService.overview();

		assertThat(overview.todayPlan().total()).isEqualTo(2);
		assertThat(overview.todayPlan().completed()).isEqualTo(1);
		assertThat(overview.todayPlan().tasks())
				.extracting(PlanTaskVO::content)
				.containsExactlyInAnyOrder("A 的甲", "A 的乙");

		assertThat(overview.upcomingAnniversaries())
				.extracting(vo -> vo.name())
				.containsExactly("A 的生日");

		assertThat(overview.memoCount()).isEqualTo(1);
	}

	/**
	 * {@code memoCount} 不是全表 COUNT。
	 *
	 * <p>单独拎出来是因为它底下的 {@code selectCount(null)} 代码里没有 WHERE，
	 * 隔离全托给拦截器 —— 这类"看不见条件"的查询在本仓库都要单独钉一条
	 * （另一条是 {@code AnniversaryServiceImpl#upcoming}）。
	 *
	 * <p><b>已验证过它确实逮得住</b>：把 {@code wb_memo} 加进
	 * {@code TABLES_WITHOUT_USER_ID} 跑一遍，本类 6 条里有 4 条当场变红，
	 * 报的是 {@code expected: 2L but was: 85L} 这种 —— 分母变成了全表条数。
	 * （具体数字取决于库里当时有多少条，别照抄。）
	 */
	@Test
	@DisplayName("备忘条数只数自己的，不是全表 COUNT")
	void memoCountIsNotGlobalCount() {
		insertMemo(USER_A, "A 的 1");
		insertMemo(USER_A, "A 的 2");
		for (int i = 0; i < 5; i++) {
			insertMemo(USER_B, "B 的 " + i);
		}

		UserContext.set(USER_A);

		assertThat(dashboardService.overview().memoCount()).isEqualTo(2);
	}

	/** 逻辑删除掉的不该再算进条数里 */
	@Test
	@DisplayName("已逻辑删除的备忘不计入条数")
	void memoCountExcludesDeleted() {
		insertMemo(USER_A, "还在的");
		insertMemo(USER_A, "删掉的");
		jdbcTemplate.update(
				"UPDATE `wb_memo` SET `deleted` = UNIX_TIMESTAMP() WHERE `user_id` = ? AND `title` = ?",
				USER_A, "删掉的");

		UserContext.set(USER_A);

		assertThat(dashboardService.overview().memoCount()).isEqualTo(1);
	}

	/**
	 * 「今日计划」只统计今天。
	 *
	 * <p>昨天和明天的任务不能被算进来 —— 这是首页上最容易悄悄错的一处：
	 * 数字大了一点，没人会觉得是 bug，只会以为"我今天记了这么多"。
	 * 顺带钉住 {@code listByDate(null)} 的语义：null 是"今天"，不是"全部"。
	 */
	@Test
	@DisplayName("今日计划只含今天的任务，昨天和明天都不算")
	void todayPlanOnlyCoversToday() {
		LocalDate today = WorkbenchTime.today();

		insertTask(USER_A, today.minusDays(1), "昨天的", 0);
		insertTask(USER_A, today, "今天的甲", 1);
		insertTask(USER_A, today, "今天的乙", 0);
		insertTask(USER_A, today.plusDays(1), "明天的", 0);

		UserContext.set(USER_A);
		DashboardVO overview = dashboardService.overview();

		assertThat(overview.todayPlan().total()).isEqualTo(2);
		assertThat(overview.todayPlan().completed()).isEqualTo(1);
		assertThat(overview.todayPlan().tasks())
				.extracting(PlanTaskVO::content)
				.containsExactlyInAnyOrder("今天的甲", "今天的乙");
	}

	/**
	 * 三个数字必须互相对得上。
	 *
	 * <p>{@code TodayPlanVO.of} 已经保证它们同源（{@code TodayPlanVOTest} 钉了纯逻辑），
	 * 这里在**接了真实数据库之后**再验一遍：从库里取出来的记录经过
	 * Mapper、拦截器、VO 转换几道手，任何一道改错了字段，两个数就会分家。
	 */
	@Test
	@DisplayName("total / completed 与 tasks 三者自洽")
	void summaryAgreesWithTaskList() {
		LocalDate today = WorkbenchTime.today();
		insertTask(USER_A, today, "甲", 1);
		insertTask(USER_A, today, "乙", 0);
		insertTask(USER_A, today, "丙", 1);
		insertTask(USER_A, today, "丁", 0);

		UserContext.set(USER_A);
		DashboardVO overview = dashboardService.overview();

		assertThat(overview.todayPlan().total())
				.isEqualTo(overview.todayPlan().tasks().size());
		assertThat(overview.todayPlan().completed())
				.isEqualTo(overview.todayPlan().tasks().stream()
						.filter(task -> task.completed() == 1)
						.count());
		assertThat(overview.todayPlan().completed()).isEqualTo(2);
	}

	/**
	 * 新用户什么都没有，首页也得能给出来。
	 *
	 * <p>这是**首次登录时**唯一会走到的分支，而它恰恰是开发时最不容易碰到的 ——
	 * 本地账号多少都攒了点数据。三个来源都为空时若有任何一个返回 null 或抛异常，
	 * 新用户打开首页就是一片报错。
	 */
	@Test
	@DisplayName("空账号返回 0 / 0 / 空列表 / 0，不抛异常")
	void emptyUserGetsZeroes() {
		UserContext.set(USER_C);
		DashboardVO overview = dashboardService.overview();

		assertThat(overview.todayPlan().total()).isZero();
		assertThat(overview.todayPlan().completed()).isZero();
		assertThat(overview.todayPlan().tasks()).isEmpty();
		assertThat(overview.upcomingAnniversaries()).isEmpty();
		assertThat(overview.memoCount()).isZero();
	}

}
