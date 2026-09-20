package org.example.workbenchserver.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.dto.SemesterDTO;
import org.example.workbenchserver.entity.Semester;
import org.example.workbenchserver.mapper.SemesterMapper;
import org.example.workbenchserver.service.SemesterService;
import org.example.workbenchserver.vo.SemesterVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code wb_semester} 的数据隔离回归测试，外加两条**跨表**的业务规则。
 *
 * <p>与 {@link PlanTaskIsolationTest} 同一套思路：数据用原生 JDBC 带显式
 * {@code user_id} 种入（绕开 MyBatis），再让 Mapper / Service 去读改写删 ——
 * 拦截器一旦失效，失败点落在**断言**上而不是插入阶段。
 *
 * <p>跨表的两条规则（本类下半部分）不是隔离，但仍然放在这里：它们要同时
 * 用上 {@code wb_semester} 和 {@code wb_course} 两张表，是"删学期 / 改周数时
 * 课程会怎样"这个问题的两个侧面。写在别处就得在两处各种一遍数据。
 *
 * <p>需要真实 MySQL，且 {@code wb_semester} / {@code wb_course} 表已建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class SemesterIsolationTest {

	/**
	 * 合成用户 id，远离真实用户规模（真实用户的自增 id 到不了 5001），
	 * 因此可以按 user_id 精确清理，不必也不能用 TRUNCATE ——
	 * 这张表在开发库里迟早会有真实数据。
	 */
	private static final long USER_A = 5001L;

	private static final long USER_B = 5002L;

	/** 一个确定的周一。第 1 周的周一，整张课表就靠它换算成真实日期 */
	private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

	@Autowired
	private SemesterMapper semesterMapper;

	@Autowired
	private SemesterService semesterService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUsers() {
		// 先删课程再删学期：课程只是"semester_id 指向它"，没有外键约束，
		// 删错顺序不会报错，只会留下一堆指向不存在学期的悬空记录
		jdbcTemplate.update("DELETE FROM `wb_course` WHERE `user_id` IN (?, ?)", USER_A, USER_B);
		jdbcTemplate.update("DELETE FROM `wb_semester` WHERE `user_id` IN (?, ?)", USER_A, USER_B);
		UserContext.clear();
	}

	// ---------- 种数据（绕过 MyBatis，显式指定 user_id） ----------

	private Long seedSemester(long userId, String name, LocalDate startDate, int totalWeeks) {
		jdbcTemplate.update(
				"INSERT INTO `wb_semester` (`user_id`, `name`, `start_date`, `total_weeks`)"
						+ " VALUES (?, ?, ?, ?)",
				userId, name, startDate, totalWeeks);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_semester` WHERE `user_id` = ? AND `name` = ?",
				Long.class, userId, name);
	}

	private Long seedCourse(long userId, Long semesterId, String name, int endWeek) {
		jdbcTemplate.update(
				"INSERT INTO `wb_course` (`user_id`, `semester_id`, `name`, `teacher`, `location`,"
						+ " `day_of_week`, `start_section`, `end_section`, `start_week`, `end_week`,"
						+ " `week_type`) VALUES (?, ?, ?, '', '', 1, 1, 2, 1, ?, 0)",
				userId, semesterId, name, endWeek);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_course` WHERE `user_id` = ? AND `name` = ?",
				Long.class, userId, name);
	}

	private List<Semester> queryAll() {
		// 条件里没有任何 user_id —— 隔离全靠拦截器
		return semesterMapper.selectList(new LambdaQueryWrapper<>());
	}

	private static SemesterDTO dto(String name, LocalDate startDate, int totalWeeks) {
		return new SemesterDTO(name, startDate, totalWeeks);
	}

	// ---------- 隔离 ----------

	@Test
	@DisplayName("插入时自动补上当前用户，业务代码无从指定归属")
	void insertAutoFillsCurrentUser() {
		UserContext.set(USER_A);

		// 刻意不设归属 —— Semester 上压根没有 userId 属性，想设也设不了
		Semester semester = new Semester();
		semester.setName("a-1");
		semester.setStartDate(MONDAY);
		semester.setTotalWeeks(18);
		semesterMapper.insert(semester);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `user_id` FROM `wb_semester` WHERE `name` = ?", Long.class, "a-1"))
				.isEqualTo(USER_A);
	}

	@Test
	@DisplayName("查询只返回当前用户的学期")
	void selectOnlyReturnsCurrentUserRows() {
		seedSemester(USER_A, "a-1", MONDAY, 18);
		seedSemester(USER_A, "a-2", MONDAY.minusWeeks(20), 16);
		seedSemester(USER_B, "b-1", MONDAY, 20);

		// 库里共有 3 行。拦截器一旦失效，这里会查出全部 3 条
		UserContext.set(USER_A);
		assertThat(queryAll())
				.extracting(Semester::getName)
				.containsExactlyInAnyOrder("a-1", "a-2");

		UserContext.set(USER_B);
		assertThat(queryAll())
				.extracting(Semester::getName)
				.containsExactly("b-1");
	}

	/**
	 * 学期列表走的是 {@code SemesterService#list}，它的 wrapper 里
	 * 只有排序、没有 user_id —— 和上面的 Mapper 查询是两条不同的路径，
	 * 而前端下拉框吃的是这一条。
	 */
	@Test
	@DisplayName("学期列表接口只返回自己的，且按开始日期倒序")
	void serviceListOnlyReturnsCurrentUserRows() {
		seedSemester(USER_A, "a-旧", MONDAY.minusWeeks(20), 16);
		seedSemester(USER_A, "a-新", MONDAY, 18);
		seedSemester(USER_B, "b-1", MONDAY, 20);

		UserContext.set(USER_A);
		assertThat(semesterService.list())
				.extracting(SemesterVO::name)
				.containsExactly("a-新", "a-旧");
	}

	@Test
	@DisplayName("拿别人的主键查不到")
	void selectByIdCannotReachAnotherUsersRow() {
		Long idOwnedByA = seedSemester(USER_A, "a-1", MONDAY, 18);

		// 主键是连续自增的，猜到别人的 id 毫无难度 —— 这是最典型的越权入口
		UserContext.set(USER_B);
		assertThat(semesterMapper.selectById(idOwnedByA)).isNull();
	}

	@Test
	@DisplayName("改不动别人的学期")
	void updateCannotModifyAnotherUsersRow() {
		Long idOwnedByA = seedSemester(USER_A, "a-1", MONDAY, 18);

		UserContext.set(USER_B);
		Semester forged = new Semester();
		forged.setId(idOwnedByA);
		forged.setName("被人改了");

		int affected = semesterMapper.updateById(forged);

		// 拦截器会给 UPDATE 也补上 user_id，所以 WHERE 匹配不到任何行
		assertThat(affected).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `name` FROM `wb_semester` WHERE `id` = ?", String.class, idOwnedByA))
				.isEqualTo("a-1");
	}

	@Test
	@DisplayName("删不掉别人的学期")
	void deleteCannotRemoveAnotherUsersRow() {
		Long idOwnedByA = seedSemester(USER_A, "a-1", MONDAY, 18);

		UserContext.set(USER_B);
		assertThat(semesterMapper.deleteById(idOwnedByA)).isZero();

		// 逻辑删除走的是 UPDATE ... SET deleted = 时间戳，确认它没被改脏
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_semester` WHERE `id` = ?", Long.class, idOwnedByA))
				.isZero();
	}

	/**
	 * 拿别人的学期 id 去改，走的是 Service 那条路（前端点的是"编辑学期"）。
	 *
	 * <p>与上面那条 Mapper 用例的区别在于：这里进的是真正被前端调用的入口，
	 * 拦截器失效时它同样会改到别人的数据上 —— 两条路都钉住。
	 */
	@Test
	@DisplayName("通过 Service 改别人的学期返回 404")
	void updateServiceRejectsAnotherUsersSemester() {
		Long idOwnedByA = seedSemester(USER_A, "a-1", MONDAY, 18);

		UserContext.set(USER_B);
		assertThatThrownBy(() -> semesterService.update(idOwnedByA, dto("被人改了", MONDAY, 18)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("学期不存在");

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `name` FROM `wb_semester` WHERE `id` = ?", String.class, idOwnedByA))
				.isEqualTo("a-1");
	}

	// ---------- 跨表的两条业务规则 ----------

	/**
	 * 删除有课程的学期必须被拒。
	 *
	 * <p>这背后是一个选择：**不级联删**。级联删的话，用户点一次"删除学期"
	 * 就悄悄丢掉一学期排好的课，而且没有撤销；拒绝则只多一步操作，
	 * 提示里还带着数量。见 docs/接口清单.md §12。
	 *
	 * <p>本用例同时验了提示里的**门数**是对的 —— 只说"还有课程"的话，
	 * 用户不知道该去删几门。
	 */
	@Test
	@DisplayName("学期下还有课程时不允许删除，并报出门数")
	void deleteRejectsSemesterWithCourses() {
		UserContext.set(USER_A);
		Long semesterId = seedSemester(USER_A, "a-1", MONDAY, 18);
		seedCourse(USER_A, semesterId, "高等数学", 16);
		seedCourse(USER_A, semesterId, "线性代数", 16);

		assertThatThrownBy(() -> semesterService.delete(semesterId))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("2 门课");

		// 学期还在（逻辑删除没执行），课程也一门没少
		assertThat(semesterMapper.selectById(semesterId)).isNotNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM `wb_course` WHERE `semester_id` = ?", Long.class, semesterId))
				.isEqualTo(2L);
	}

	/**
	 * 课程的"存在"要按**当前用户**算。
	 *
	 * <p>这条挡的是一个只在特定数据下才出现的场景：A 和 B 各有一个学期，
	 * 若 {@code countCourses} 不隔离（拦截器失效），A 删自己的空学期时
	 * 会把 B 的课也算进来，于是**删不掉一个根本没有课的学期**，
	 * 而提示还会说"该学期下还有 3 门课" —— 用户完全无从下手。
	 */
	@Test
	@DisplayName("别人学期里的课不算在自己学期头上")
	void deleteIsNotBlockedByAnotherUsersCourses() {
		Long semesterIdOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long semesterIdOfB = seedSemester(USER_B, "b-1", MONDAY, 18);
		seedCourse(USER_B, semesterIdOfB, "别人的课", 16);

		UserContext.set(USER_A);
		semesterService.delete(semesterIdOfA);

		assertThat(semesterMapper.selectById(semesterIdOfA)).isNull();
	}

	/** 没有课程时删得掉 —— 否则上面那条规则可能只是"永远拒绝"，看着也全绿 */
	@Test
	@DisplayName("学期下没有课程时可以正常删除")
	void deleteSucceedsWhenNoCourses() {
		UserContext.set(USER_A);
		Long semesterId = seedSemester(USER_A, "a-1", MONDAY, 18);

		semesterService.delete(semesterId);

		assertThat(semesterMapper.selectById(semesterId)).isNull();
	}

	/**
	 * 把总周数改小到课程的结束周之外，必须被拒。
	 *
	 * <p><b>这条规则接口清单里没有写</b>，是照"禁止删除有课程的学期"的同一思路补的：
	 * 20 周改成 16 周之后，排在第 18 周的课既没被删、也不再出现在任何一格上。
	 * 数据还在库里，只是从此看不见了，而用户只是改了个数字。
	 *
	 * <p>断言里带上课程名和它排到的周数：只知道"有课程超范围"的话，
	 * 用户不知道该改哪一门。
	 */
	@Test
	@DisplayName("改小总周数时，超出范围的课程会拦住这次修改")
	void updateRejectsShrinkingBelowCourseEndWeek() {
		UserContext.set(USER_A);
		Long semesterId = seedSemester(USER_A, "a-1", MONDAY, 20);
		seedCourse(USER_A, semesterId, "高等数学", 18);

		assertThatThrownBy(() -> semesterService.update(semesterId, dto("a-1", MONDAY, 16)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("高等数学")
				.hasMessageContaining("第 18 周");

		// 周数没被改掉 —— 报错而不是"拒绝一部分"
		assertThat(semesterMapper.selectById(semesterId).getTotalWeeks()).isEqualTo(20);
	}

	/** 周数改到课程范围内就可以（18 周的课，从 20 周改成 18 周恰好合法） */
	@Test
	@DisplayName("总周数仍覆盖所有课程时可以改小")
	void updateAllowsShrinkingToExactlyTheLastCourseWeek() {
		UserContext.set(USER_A);
		Long semesterId = seedSemester(USER_A, "a-1", MONDAY, 20);
		seedCourse(USER_A, semesterId, "高等数学", 18);

		semesterService.update(semesterId, dto("a-1", MONDAY, 18));

		assertThat(semesterMapper.selectById(semesterId).getTotalWeeks()).isEqualTo(18);
	}

	/**
	 * 开始日期必须是周一。
	 *
	 * <p>这是整张课表的地基：第 N 周星期几 = {@code startDate + (N-1)*7 + (星期几-1)}。
	 * 基准偏一天，整学期的课就整体偏一天 —— 而界面上显示的仍是一张
	 * 看着完全正常的课表，没有任何一步会报错。
	 */
	@Test
	@DisplayName("开始日期不是周一时报 400，并说明那天是星期几")
	void createRejectsNonMondayStartDate() {
		UserContext.set(USER_A);
		LocalDate tuesday = MONDAY.plusDays(1);

		assertThatThrownBy(() -> semesterService.create(dto("a-1", tuesday, 18)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("周一")
				.hasMessageContaining("周二");

		assertThat(queryAll()).isEmpty();
	}

	/** 边界：周一可以通过，且总周数 1 与 60 都在允许范围内 */
	@Test
	@DisplayName("周一的开始日期可以创建")
	void createAcceptsMonday() {
		UserContext.set(USER_A);

		SemesterVO created = semesterService.create(dto("a-1", MONDAY, 18));

		assertThat(created.startDate()).isEqualTo(MONDAY);
		assertThat(created.totalWeeks()).isEqualTo(18);
	}

	// ---------- 其余几条 Mapper 层面的行为 ----------

	/** 结构化确认：实体上那两列确实是数据库在管（同 EntityTimestampConventionTest 的用意） */
	@Test
	@DisplayName("创建时间由数据库填，插入后能读回来")
	void createTimeIsFilledByDatabase() {
		UserContext.set(USER_A);

		SemesterVO created = semesterService.create(dto("a-1", MONDAY, 18));

		// 若 Service 忘了插完回读一次，这里就是 null
		assertThat(created.createTime()).isNotNull();
		assertThat(created.updateTime()).isNotNull();
	}

	/**
	 * 课程在本类里只是"用来挡住删学期"的背景数据，这里顺带确认它真的落库了。
	 *
	 * <p>查询**必须带上 {@code user_id}**：这个断言只用原始 JDBC 读一行回来，
	 * 而库是所有环境共用的开发库 —— 别的验证（浏览器脚本、手工点的）也会往
	 * {@code wb_course} 里写同名课程。少了这个条件，`queryForObject` 会在
	 * "数量不是 1"时抛 {@code IncorrectResultSizeDataAccessException}，
	 * 而那跟被测的事情毫无关系：这条用例本来就是要确认"这一行挂在那个学期下"，
	 * 谁的名字撞上了本不该影响它。
	 */
	@Test
	@DisplayName("种入的课程确实挂在那个学期下")
	void seededCourseBelongsToSemester() {
		Long semesterId = seedSemester(USER_A, "a-1", MONDAY, 18);
		seedCourse(USER_A, semesterId, "高等数学", 16);

		UserContext.set(USER_A);
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `semester_id` FROM `wb_course` WHERE `user_id` = ? AND `name` = ?",
				Long.class, USER_A, "高等数学"))
				.isEqualTo(semesterId);
	}

}
