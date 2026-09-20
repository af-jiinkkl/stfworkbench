package org.example.workbenchserver.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.dto.CourseDTO;
import org.example.workbenchserver.entity.Course;
import org.example.workbenchserver.mapper.CourseMapper;
import org.example.workbenchserver.service.CourseService;
import org.example.workbenchserver.vo.CourseVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * {@code wb_course} 的数据隔离回归测试。
 *
 * <p>与 {@link PlanTaskIsolationTest} 同一套思路：数据用原生 JDBC 带显式
 * {@code user_id} 种入（绕开 MyBatis），再让 Mapper / Service 去读改写删 ——
 * 拦截器一旦失效，失败点落在**断言**上而不是插入阶段。
 *
 * <p>除常规的隔离用例之外，本类有三条本模块**特有**的，都围绕同一个问题：
 * <b>租户插件管不到 {@code semester_id} 指向谁</b>。
 *
 * <ul>
 *   <li>{@link #createRejectsAnotherUsersSemester()} —— 拿别人的学期 id 建课。
 *       拦截器不会拦，库里会多出一条指向别人学期的记录 —— 这是本模块
 *       最要紧的一条越权通道</li>
 *   <li>{@link #listBySemesterHidesCoursesEscalatedIntoIt()} —— 越权写入**成功之后**
 *       的样子：别人的课挂在你的学期下，你查这个学期的课表时不能看见它</li>
 *   <li>{@link #listOnDateDoesNotLeakOtherUsersCourses()} —— 与
 *       {@code AnniversaryIsolationTest#upcomingDoesNotLeakOtherUsersRecords} 同类：
 *       找"今天属于哪个学期"那一步的 SQL 里**一个字都没有**，隔离全靠拦截器</li>
 * </ul>
 *
 * <p>需要真实 MySQL，且 {@code wb_semester} / {@code wb_course} 表已建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class CourseIsolationTest {

	/** 合成用户 id，远离真实用户规模，可按 user_id 精确清理。本类不用 TRUNCATE —— 表里有真数据 */
	private static final long USER_A = 6001L;

	private static final long USER_B = 6002L;

	/** 一个确定的周一。本类里所有学期都从这一天开始，省得每处都算一遍 */
	private static final LocalDate MONDAY = LocalDate.of(2026, 3, 2);

	@Autowired
	private CourseMapper courseMapper;

	@Autowired
	private CourseService courseService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUsers() {
		// 先删课程再删学期（课程只是"semester_id 指向它"，没有外键约束兜底）
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

	private Long seedCourse(long userId, Long semesterId, String name, int dayOfWeek, int weekType) {
		jdbcTemplate.update(
				"INSERT INTO `wb_course` (`user_id`, `semester_id`, `name`, `teacher`, `location`,"
						+ " `day_of_week`, `start_section`, `end_section`, `start_week`, `end_week`,"
						+ " `week_type`) VALUES (?, ?, ?, '', '', ?, 1, 2, 1, 18, ?)",
				userId, semesterId, name, dayOfWeek, weekType);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_course` WHERE `user_id` = ? AND `name` = ?",
				Long.class, userId, name);
	}

	private List<Course> queryAll() {
		// 条件里没有任何 user_id —— 隔离全靠拦截器
		return courseMapper.selectList(new LambdaQueryWrapper<>());
	}

	private long rawCount(long userId) {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM `wb_course` WHERE `user_id` = ?", Long.class, userId);
	}

	/** 一条合法的入参：周一 1-2 节，第 1 到 18 周，每周 */
	private static CourseDTO dto(Long semesterId, String name) {
		return new CourseDTO(semesterId, name, "张老师", "教三 201", 1, 1, 2, 1, 18, 0);
	}

	private static CourseDTO dto(Long semesterId, String name, int dayOfWeek, int weekType) {
		return new CourseDTO(semesterId, name, "张老师", "教三 201", dayOfWeek, 1, 2, 1, 18, weekType);
	}

	// ---------- 隔离 ----------

	@Test
	@DisplayName("插入时自动补上当前用户，业务代码无从指定归属")
	void insertAutoFillsCurrentUser() {
		Long semesterId = seedSemester(USER_A, "a-1", MONDAY, 18);
		UserContext.set(USER_A);

		// 刻意不设归属 —— Course 上压根没有 userId 属性，想设也设不了
		Course course = new Course();
		course.setSemesterId(semesterId);
		course.setName("a-1");
		course.setTeacher("");
		course.setLocation("");
		course.setDayOfWeek(1);
		course.setStartSection(1);
		course.setEndSection(2);
		course.setStartWeek(1);
		course.setEndWeek(18);
		course.setWeekType(0);
		courseMapper.insert(course);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `user_id` FROM `wb_course` WHERE `name` = ?", Long.class, "a-1"))
				.isEqualTo(USER_A);
	}

	@Test
	@DisplayName("查询只返回当前用户的课程")
	void selectOnlyReturnsCurrentUserRows() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long semesterOfB = seedSemester(USER_B, "b-1", MONDAY, 18);
		seedCourse(USER_A, semesterOfA, "a-1", 1, 0);
		seedCourse(USER_A, semesterOfA, "a-2", 3, 0);
		seedCourse(USER_B, semesterOfB, "b-1", 1, 0);

		// 库里共有 3 行。拦截器一旦失效，这里会查出全部 3 条
		UserContext.set(USER_A);
		assertThat(queryAll())
				.extracting(Course::getName)
				.containsExactlyInAnyOrder("a-1", "a-2");

		UserContext.set(USER_B);
		assertThat(queryAll())
				.extracting(Course::getName)
				.containsExactly("b-1");
	}

	@Test
	@DisplayName("拿别人的主键查不到")
	void selectByIdCannotReachAnotherUsersRow() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long idOwnedByA = seedCourse(USER_A, semesterOfA, "a-1", 1, 0);

		// 主键是连续自增的，猜到别人的 id 毫无难度 —— 这是最典型的越权入口
		UserContext.set(USER_B);
		assertThat(courseMapper.selectById(idOwnedByA)).isNull();
	}

	@Test
	@DisplayName("改不动别人的课")
	void updateCannotModifyAnotherUsersRow() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long idOwnedByA = seedCourse(USER_A, semesterOfA, "a-1", 1, 0);

		UserContext.set(USER_B);
		Course forged = new Course();
		forged.setId(idOwnedByA);
		forged.setName("被人改了");

		int affected = courseMapper.updateById(forged);

		// 拦截器会给 UPDATE 也补上 user_id，所以 WHERE 匹配不到任何行
		assertThat(affected).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `name` FROM `wb_course` WHERE `id` = ?", String.class, idOwnedByA))
				.isEqualTo("a-1");
	}

	@Test
	@DisplayName("删不掉别人的课")
	void deleteCannotRemoveAnotherUsersRow() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long idOwnedByA = seedCourse(USER_A, semesterOfA, "a-1", 1, 0);

		UserContext.set(USER_B);
		assertThat(courseMapper.deleteById(idOwnedByA)).isZero();

		// 逻辑删除走的是 UPDATE ... SET deleted = 时间戳，确认它没被改脏
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_course` WHERE `id` = ?", Long.class, idOwnedByA))
				.isZero();
	}

	@Test
	@DisplayName("通过 Service 删别人的课返回 404")
	void deleteServiceRejectsAnotherUsersCourse() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long idOwnedByA = seedCourse(USER_A, semesterOfA, "a-1", 1, 0);

		UserContext.set(USER_B);
		assertThatThrownBy(() -> courseService.delete(idOwnedByA))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("课程不存在");
	}

	// ---------- 本模块特有：semester_id 指向谁，拦截器管不着 ----------

	/**
	 * <b>本类最要紧的一条。</b>拿别人的学期 id 来建课，必须被拒。
	 *
	 * <p>租户插件管的是 {@code wb_course.user_id}，管不到 {@code semester_id}
	 * 指向谁 —— 少了 {@code requireOwnedSemester} 这一步，这次写库会成功，
	 * 而且**不会报任何错**：记录的 user_id 是写入者自己的（拦截器补的），
	 * 所以它既不出现在被指向那个人的课表里，也不在写入者的课表里
	 * （他的前端手上没有这个 semesterId 的入口）。一条谁也不认识的数据。
	 *
	 * <p>所以断言分两部分：抛 404，并且**库里一行都没多**。
	 * 只看异常的话，一个"抛了异常但已经插进去了"的实现照样通过。
	 */
	@Test
	@DisplayName("拿别人的学期 id 建课返回 404，且库里一行都不多")
	void createRejectsAnotherUsersSemester() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		long before = rawCount(USER_B);

		UserContext.set(USER_B);
		assertThatThrownBy(() -> courseService.create(dto(semesterOfA, "越权写入的课")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("学期不存在");

		assertThat(rawCount(USER_B)).isEqualTo(before);
		assertThat(queryAll()).isEmpty();
	}

	/** 改课也一样：不能把自己的课挪到别人的学期下（同样是"写入一个悬空引用"） */
	@Test
	@DisplayName("把自己的课挪到别人的学期下返回 404，学期归属不变")
	void updateRejectsMovingCourseIntoAnotherUsersSemester() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long semesterOfB = seedSemester(USER_B, "b-1", MONDAY, 18);
		Long courseOfB = seedCourse(USER_B, semesterOfB, "b-1", 1, 0);

		UserContext.set(USER_B);
		assertThatThrownBy(() -> courseService.update(courseOfB, dto(semesterOfA, "b-1")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("学期不存在");

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `semester_id` FROM `wb_course` WHERE `id` = ?", Long.class, courseOfB))
				.isEqualTo(semesterOfB);
	}

	/** 拿别人的学期 id 查课表，同样返回 404 而不是空列表 —— 404 不额外泄露什么，空列表反而像是"这个学期没排课" */
	@Test
	@DisplayName("查别人的学期的课表返回 404")
	void listBySemesterRejectsAnotherUsersSemester() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		seedCourse(USER_A, semesterOfA, "a-1", 1, 0);

		UserContext.set(USER_B);
		assertThatThrownBy(() -> courseService.listBySemester(semesterOfA))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("学期不存在");
	}

	/**
	 * 越权写入**已经发生之后**的样子：别人的课挂在你的学期下面
	 * （{@code user_id} 是别人的，{@code semester_id} 是你的）。
	 *
	 * <p>这条数据不该存在 —— 上面那条用例就是拦它的。但拦漏了会怎样，
	 * 值得单独钉住：你的课表页按 {@code semesterId} 取课，若 {@code listBySemester}
	 * 只按 semesterId 筛、不靠拦截器，别人的课名就会出现在你的课表上。
	 *
	 * <p>注意这里只能靠 {@code user_id} 隔离 —— 那条记录**确实**属于这个学期，
	 * 属于别人是唯一能把它挡在外面的理由。
	 */
	@Test
	@DisplayName("即使别人的课挂在自己的学期下，课表里也看不到")
	void listBySemesterHidesCoursesEscalatedIntoIt() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		seedCourse(USER_A, semesterOfA, "a-1", 1, 0);
		// 模拟一次拦漏了的越权写入：user_id 是 B，semester_id 是 A 的
		seedCourse(USER_B, semesterOfA, "b-越权挂进来的", 2, 0);

		UserContext.set(USER_A);
		assertThat(courseService.listBySemester(semesterOfA))
				.extracting(CourseVO::name)
				.containsExactly("a-1");
	}

	/**
	 * 与 {@code AnniversaryIsolationTest#upcomingDoesNotLeakOtherUsersRecords} 同类。
	 *
	 * <p>首页的"今日课程"要先找"今天属于哪个学期"，那一步是
	 * {@code semesterMapper.selectList(null)} —— <b>SQL 里一个字都没有</b>，
	 * 隔离全靠拦截器。找错学期的话，接着查出来的课也是别人的。
	 *
	 * <p>这里让 A 和 B 各有一个**都包含今天**的学期，各排一门今天的课：
	 * 拦截器一旦失效，两个人在首页上都会看到两门课。
	 */
	@Test
	@DisplayName("首页的今日课程不会带出别人的课")
	void listOnDateDoesNotLeakOtherUsersCourses() {
		LocalDate today = WorkbenchTime.today();
		int todayOfWeek = today.getDayOfWeek().getValue();
		// 让学期从本周一开始，今天必然落在第 1 周之内
		LocalDate monday = today.with(DayOfWeek.MONDAY);

		Long semesterOfA = seedSemester(USER_A, "a-1", monday, 18);
		Long semesterOfB = seedSemester(USER_B, "b-1", monday, 18);
		seedCourse(USER_A, semesterOfA, "a-今天的课", todayOfWeek, 0);
		seedCourse(USER_B, semesterOfB, "b-今天的课", todayOfWeek, 0);

		UserContext.set(USER_A);
		assertThat(courseService.listOnDate(today))
				.extracting(CourseVO::name)
				.containsExactly("a-今天的课");

		UserContext.set(USER_B);
		assertThat(courseService.listOnDate(today))
				.extracting(CourseVO::name)
				.containsExactly("b-今天的课");
	}

	/** 逻辑删除本身的行为，与隔离无关 —— 它验证"删除后本人也查不到，但库里那行还在" */
	@Test
	@DisplayName("逻辑删除后本人也查不到，但数据仍在库里")
	void logicDeleteHidesRowFromOwnerToo() {
		Long semesterOfA = seedSemester(USER_A, "a-1", MONDAY, 18);
		Long id = seedCourse(USER_A, semesterOfA, "a-1", 1, 0);

		UserContext.set(USER_A);
		assertThat(courseMapper.deleteById(id)).isEqualTo(1);

		assertThat(courseMapper.selectById(id)).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_course` WHERE `id` = ?", Long.class, id))
				.isNotZero();
	}

}
