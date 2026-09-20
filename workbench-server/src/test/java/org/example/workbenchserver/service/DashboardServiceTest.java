package org.example.workbenchserver.service;

import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.security.UserContext;
import org.example.workbenchserver.vo.CourseVO;
import org.example.workbenchserver.vo.DashboardVO;
import org.example.workbenchserver.vo.PlanTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 首页聚合接口的数据来源与隔离。
 *
 * <p>这个接口是全仓库唯一一个**一次返回五张表**的地方，所以两类风险都集中在这里：
 *
 * <ol>
 *   <li><b>隔离</b>：五份数据来自五张表，任何一处拦截器失效，
 *       首页上就会冒出别人的东西。其中 {@code memoCount} 最危险 ——
 *       它底层是 {@code selectCount(null)}，代码里一个谓词都没有，
 *       WHERE 完全由拦截器拼出来（见 {@code MemoServiceImpl#count}），
 *       失效时不是一个字段错，而是直接变成全表 COUNT</li>
 *   <li><b>自洽</b>：{@code total} / {@code completed} / {@code tasks} 三个数
 *       必须对得上，且只统计"今天"；{@code todayExpenseAmount} 与
 *       {@code todayCourses} 同理，算的都是**今天**的，不是全部。
 *       这类错不抛异常，只是首页的数字悄悄不对 ——
 *       而"今天花了 386 块"这种数没人会去核，
 *       "今天有几节课"也一样（漏掉一门单周的课，看着就像那天本来没课）</li>
 * </ol>
 *
 * <p>数据用原生 {@code JdbcTemplate} 带显式 {@code user_id} 种入，绕开 MyBatis ——
 * 理由同 {@code PlanTaskIsolationTest}：若改用 Mapper 插，拦截器一失效就会在
 * **插入**阶段抛 NOT NULL，测试红了却红在错误位置，真正的断言根本没跑过。
 *
 * <p>需要真实 MySQL，且五张表都已建好（见 db/schema.sql）。
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
		// 顺序只是为了让读的人先看到课程再看到学期（"课程挂在学期下"）。
		// 两张表之间没有外键约束，删的先后其实无所谓
		for (String table : new String[] {"wb_plan_task", "wb_anniversary", "wb_memo", "wb_expense",
				"wb_course", "wb_semester"}) {
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

	private void insertExpense(long userId, LocalDate date, String amount) {
		// 金额传**字符串**而不是 double：double 的 10.10 存进去会被四舍五入成
		// 10.099999999999999，于是断言里那个数字是从一个已经错了的值算来的，
		// 就算实现真的算错了也未必看得出来
		jdbcTemplate.update(
				"INSERT INTO `wb_expense` (`user_id`, `amount`, `category`, `expense_date`, `remark`) "
						+ "VALUES (?, ?, '餐饮', ?, '')",
				userId, amount, date);
	}

	/** 种一个学期，返回 id。`startDate` 必须是周一（库里没有这个约束，用的人自己保证） */
	private Long insertSemester(long userId, String name, LocalDate startDate, int totalWeeks) {
		jdbcTemplate.update(
				"INSERT INTO `wb_semester` (`user_id`, `name`, `start_date`, `total_weeks`) "
						+ "VALUES (?, ?, ?, ?)",
				userId, name, startDate, totalWeeks);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_semester` WHERE `user_id` = ? AND `name` = ?",
				Long.class, userId, name);
	}

	private void insertCourse(long userId, Long semesterId, String name, int dayOfWeek,
			int startWeek, int endWeek, int weekType) {
		jdbcTemplate.update(
				"INSERT INTO `wb_course` (`user_id`, `semester_id`, `name`, `teacher`, `location`,"
						+ " `day_of_week`, `start_section`, `end_section`, `start_week`, `end_week`,"
						+ " `week_type`) VALUES (?, ?, ?, '', '', ?, 1, 2, ?, ?, ?)",
				userId, semesterId, name, dayOfWeek, startWeek, endWeek, weekType);
	}

	/** 本学期的第 1 周的周一 —— 种"今天有课"的数据时用它当学期起点，今天必然落在第 1 周内 */
	private static LocalDate thisMonday() {
		return WorkbenchTime.today().with(DayOfWeek.MONDAY);
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

		insertExpense(USER_A, today, "10.50");
		insertExpense(USER_A, today, "20.25");
		// B 的金额刻意大得离谱：隔离一旦失效，合计会变成一万多，
		// 与"算错了小数点"那种失败一眼就分得开
		insertExpense(USER_B, today, "9999.00");

		Long semesterA = insertSemester(USER_A, "A 的学期", thisMonday(), 18);
		Long semesterB = insertSemester(USER_B, "B 的学期", thisMonday(), 18);
		insertCourse(USER_A, semesterA, "A 的课", today.getDayOfWeek().getValue(), 1, 18, 0);
		insertCourse(USER_B, semesterB, "B 的课", today.getDayOfWeek().getValue(), 1, 18, 0);

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

		assertThat(overview.todayExpenseAmount()).isEqualByComparingTo("30.75");

		assertThat(overview.todayCourses())
				.extracting(CourseVO::name)
				.containsExactly("A 的课");
	}

	/**
	 * 「今日消费」只算今天，且只算没被删掉的。
	 *
	 * <p>昨天和明天的那两笔是关键：这个数走的是
	 * {@code ExpenseService#sumOf(today, today)}，区间由 DashboardServiceImpl 给出。
	 * 一旦哪天有人图省事把它改成"不限区间"，昨天和明天的钱就会一起算进"今天花了多少" ——
	 * 数字大一点，没人会觉得是 bug，只会以为今天确实花了这么多。
	 *
	 * <p>软删的那笔同理：它必须被 {@code @TableLogic} 挡在 WHERE 之外。
	 */
	@Test
	@DisplayName("今日消费只算今天的、未删除的记录")
	void todayExpenseOnlyCoversToday() {
		LocalDate today = WorkbenchTime.today();

		insertExpense(USER_A, today.minusDays(1), "5.00");
		insertExpense(USER_A, today, "12.30");
		insertExpense(USER_A, today, "7.70");
		insertExpense(USER_A, today.plusDays(1), "100.00");

		insertExpense(USER_A, today, "88.88");
		jdbcTemplate.update(
				"UPDATE `wb_expense` SET `deleted` = UNIX_TIMESTAMP() "
						+ "WHERE `user_id` = ? AND `amount` = 88.88",
				USER_A);

		UserContext.set(USER_A);

		assertThat(dashboardService.overview().todayExpenseAmount()).isEqualByComparingTo("20.00");
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
	 * 「今日课程」只含**今天**的课。
	 *
	 * <p>这是全仓库唯一一处要在 Java 里算"今天是第几周的第几天"的地方，
	 * 一共要过四道判断才落到某一门课上，每一道错了都只是**安静地漏掉一门课**：
	 *
	 * <ol>
	 *   <li>今天落在哪个学期内（{@code start_date} 起、{@code totalWeeks * 7} 天止）</li>
	 *   <li>今天是第几周 —— 单双周按它判，不是按日历周</li>
	 *   <li>今天星期几，与课程的 {@code day_of_week} 比</li>
	 *   <li>这门课的周次区间是否覆盖本周</li>
	 * </ol>
	 *
	 * <p>下面种了六门课，只有两门该出现；其余四门各自代表上述判断的一种失败方式 ——
	 * 任何一处改错，界面上看着就是"那天没课"，而不是报错。
	 */
	@Test
	@DisplayName("今日课程只含今天的课：别的星期、别的周次、别的单双周都不算")
	void todayCoursesOnlyCoverToday() {
		LocalDate today = WorkbenchTime.today();
		int todayOfWeek = today.getDayOfWeek().getValue();
		int anotherDay = todayOfWeek == 1 ? 2 : 1;

		Long semesterA = insertSemester(USER_A, "本学期", thisMonday(), 18);
		// 该出现：今天、第 1 周起、每周
		insertCourse(USER_A, semesterA, "今天的课", todayOfWeek, 1, 18, 0);
		// 不该出现：别的星期几
		insertCourse(USER_A, semesterA, "别的日子的课", anotherDay, 1, 18, 0);
		// 不该出现：从第 2 周才开始，今天在第 1 周
		insertCourse(USER_A, semesterA, "以后才开始的课", todayOfWeek, 2, 18, 0);
		// 不该出现：双周。今天在第 1 周，是单周
		insertCourse(USER_A, semesterA, "双周的课", todayOfWeek, 1, 18, 2);
		// 该出现：单周
		insertCourse(USER_A, semesterA, "单周的课", todayOfWeek, 1, 18, 1);

		// 不该出现：学期下周才开始，今天不在它里面。
		// 注意这条课的 day_of_week 也是"今天" —— 挡住它的只可能是"学期不覆盖今天"
		Long semesterFuture = insertSemester(USER_A, "下学期", thisMonday().plusWeeks(1), 18);
		insertCourse(USER_A, semesterFuture, "未来学期的课", todayOfWeek, 1, 18, 0);

		UserContext.set(USER_A);
		DashboardVO overview = dashboardService.overview();

		assertThat(overview.todayCourses())
				.extracting(CourseVO::name)
				.containsExactlyInAnyOrder("今天的课", "单周的课");
	}

	/**
	 * 今日课程也只含自己的。
	 *
	 * <p>A 和 B 各有一个**都包含今天**的学期，各排一门今天的课。
	 * 这里挡不住的东西尤其多：找"今天属于哪个学期"那一步的 SQL 里**一个字都没有**
	 * （{@code semesterMapper.selectList(null)}，见 {@code CourseServiceImpl#listOnDate}），
	 * 隔离全靠拦截器 —— 失效时 B 会直接把自己那个学期当成"A 的当前学期"，
	 * 于是 A 的首页上出现 B 的课表。
	 */
	@Test
	@DisplayName("今日课程不含别人的")
	void todayCoursesExcludeOtherUsers() {
		LocalDate today = WorkbenchTime.today();
		int todayOfWeek = today.getDayOfWeek().getValue();

		Long semesterA = insertSemester(USER_A, "A 的学期", thisMonday(), 18);
		Long semesterB = insertSemester(USER_B, "B 的学期", thisMonday(), 18);
		insertCourse(USER_A, semesterA, "A 的课", todayOfWeek, 1, 18, 0);
		insertCourse(USER_B, semesterB, "B 的课", todayOfWeek, 1, 18, 0);

		UserContext.set(USER_A);
		assertThat(dashboardService.overview().todayCourses())
				.extracting(CourseVO::name)
				.containsExactly("A 的课");

		UserContext.set(USER_B);
		assertThat(dashboardService.overview().todayCourses())
				.extracting(CourseVO::name)
				.containsExactly("B 的课");
	}

	/**
	 * 今天不属于任何学期时是**空列表**，不是错误。
	 *
	 * <p>寒暑假里每天都会走这条路。它若抛异常，用户一放假首页就打不开了 ——
	 * 而那是最不该出问题的时候（正好放假，想看的是别的东西）。
	 */
	@Test
	@DisplayName("没有学期覆盖今天时今日课程是空列表")
	void todayCoursesAreEmptyOutsideAnySemester() {
		LocalDate today = WorkbenchTime.today();
		// 学期已经结束了：从 4 周前开始、只排 2 周，今天在它之后
		Long finished = insertSemester(USER_A, "早已结束", thisMonday().minusWeeks(4), 2);
		insertCourse(USER_A, finished, "上学期的课", today.getDayOfWeek().getValue(), 1, 2, 0);

		UserContext.set(USER_A);

		assertThat(dashboardService.overview().todayCourses()).isEmpty();
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
	 * 本地账号多少都攒了点数据。几个来源都为空时若有任何一个返回 null 或抛异常，
	 * 新用户打开首页就是一片报错。
	 *
	 * <p>消费合计这里多钉一句 {@code toPlainString()}：它必须是 {@code 0.00}
	 * 而不是 {@code 0} 或 null。空区间下这个值来自
	 * {@code ExpenseServiceImpl#sumOf} 的 {@code reduce} 初值 ——
	 * 没有记录时它压根不参与求和，换个写法（比如从数据库 SUM 出来再兜底）
	 * 很容易漏掉小数位，而前端拿到 {@code 0} 会显示成"¥0"而不是"¥0.00"。
	 * 这种不一致只在**新账号**上出现，开发时几乎看不到。
	 */
	@Test
	@DisplayName("空账号返回 0 / 空课表 / 0 / 0.00，不抛异常")
	void emptyUserGetsZeroes() {
		UserContext.set(USER_C);
		DashboardVO overview = dashboardService.overview();

		assertThat(overview.todayPlan().total()).isZero();
		assertThat(overview.todayPlan().completed()).isZero();
		assertThat(overview.todayPlan().tasks()).isEmpty();
		assertThat(overview.upcomingAnniversaries()).isEmpty();
		// 没建学期的新账号：今日课程这条走的是"找不到包含今天的学期"那个分支，
		// 必须给空列表而不是 null（前端拿到 null 会在 .length 上炸）
		assertThat(overview.todayCourses()).isEmpty();
		assertThat(overview.memoCount()).isZero();
		assertThat(overview.todayExpenseAmount()).isNotNull();
		assertThat(overview.todayExpenseAmount().toPlainString()).isEqualTo("0.00");
	}

}
