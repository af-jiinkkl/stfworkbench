package org.example.workbenchserver.service;

import org.example.workbenchserver.common.CourseTime;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.dto.CourseDTO;
import org.example.workbenchserver.dto.SemesterDTO;
import org.example.workbenchserver.security.UserContext;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 课程 Service 层的行为用例。
 *
 * <p>和 {@code CourseIsolationTest} 分工不同：那边验"看不到别人的数据"，
 * 这边验"自己的数据长什么样、非法的入参被不被拦"。四条主线：
 *
 * <ul>
 *   <li><b>{@code listOnDate} 的换算</b> —— "今天"要经过"属于哪个学期 → 第几周 →
 *       星期几"三步才落到具体的课上。这是本模块唯一一处日期推算，
 *       也是唯一一处会把课"安静地漏掉"的地方</li>
 *   <li><b>周次的上界是学期的总周数</b>，不是 {@code CourseTime} 里的全局上限</li>
 *   <li><b>节次 / 星期 / 周类型的范围与前后关系</b></li>
 *   <li><b>写路径的响应形状</b> —— 与备忘录、消费同一个坑：PUT 清空老师或地点时，
 *       null 会被 MyBatis-Plus 跳过，旧值留在库里</li>
 * </ul>
 *
 * <p>需要真实 MySQL，且 {@code wb_semester} / {@code wb_course} 表已建好。
 */
@SpringBootTest
class CourseServiceTest {

	/** 合成用户 id，远离真实用户规模，可按 user_id 精确清理 */
	private static final long USER_ID = 6010L;

	/** 本周的周一。让"今天"必然落在以此为起点的学期第 1 周内 */
	private static final LocalDate THIS_MONDAY = WorkbenchTime.today().with(DayOfWeek.MONDAY);

	@Autowired
	private CourseService courseService;

	@Autowired
	private SemesterService semesterService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUpTestUser() {
		cleanTestUser();
		// 本类的每个用例都以同一个用户身份跑。Service 里没有 userId 参数，
		// 当前用户只可能来自这里
		UserContext.set(USER_ID);
	}

	@AfterEach
	void clearTestUser() {
		cleanTestUser();
		// 测试跑在同一个线程上，不清的话下个测试类会读到这里的用户身份
		UserContext.clear();
	}

	private void cleanTestUser() {
		// 先删课程再删学期（课程只是"semester_id 指向它"，没有外键约束兜底）
		jdbcTemplate.update("DELETE FROM `wb_course` WHERE `user_id` = ?", USER_ID);
		jdbcTemplate.update("DELETE FROM `wb_semester` WHERE `user_id` = ?", USER_ID);
	}

	/** 建一个从本周一开始、18 周的学期，返回它的 id */
	private Long semester() {
		return semester(THIS_MONDAY, 18);
	}

	private Long semester(LocalDate startDate, int totalWeeks) {
		return semesterService.create(new SemesterDTO("测试学期", startDate, totalWeeks)).id();
	}

	private static CourseDTO dto(Long semesterId, String name, int dayOfWeek,
			int startSection, int endSection, int startWeek, int endWeek, Integer weekType) {
		return new CourseDTO(semesterId, name, "张老师", "教三 201",
				dayOfWeek, startSection, endSection, startWeek, endWeek, weekType);
	}

	private static CourseDTO weekly(Long semesterId, String name, int dayOfWeek) {
		return dto(semesterId, name, dayOfWeek, 1, 2, 1, 18, CourseTime.WEEK_TYPE_EVERY);
	}

	// ---------- listOnDate 的换算 ----------

	@Test
	@DisplayName("今日课程只取今天那个星期几的课")
	void listOnDateFiltersByDayOfWeek() {
		Long semesterId = semester();
		int today = WorkbenchTime.today().getDayOfWeek().getValue();
		// 取一个**不是**今天的星期几，用来确认它被筛掉了
		int anotherDay = today == 1 ? 2 : 1;

		courseService.create(weekly(semesterId, "今天的课", today));
		courseService.create(weekly(semesterId, "别的日子的课", anotherDay));

		assertThat(courseService.listOnDate(WorkbenchTime.today()))
				.extracting(CourseVO::name)
				.containsExactly("今天的课");
	}

	/**
	 * 单双周在 {@code listOnDate} 里也生效，用的必须是**学期第几周**。
	 *
	 * <p>本周是第 1 周（单周），所以：每周的课上、单周的课上、双周的不上。
	 * 这条同时钉住了"第 1 周算单周"这个约定 —— 若哪天有人改成按日历周判，
	 * 本周恰好是奇数周还是偶数周取决于当年，这条会变得时好时坏，
	 * 但只要学期起点是"本周一"，第 1 周就恒为单周。
	 */
	@Test
	@DisplayName("单双周按学期第几周判：第 1 周上单周课，不上双周课")
	void listOnDateFiltersByWeekType() {
		Long semesterId = semester();
		int today = WorkbenchTime.today().getDayOfWeek().getValue();

		courseService.create(dto(semesterId, "每周", today, 1, 2, 1, 18, CourseTime.WEEK_TYPE_EVERY));
		courseService.create(dto(semesterId, "单周", today, 3, 4, 1, 18, CourseTime.WEEK_TYPE_ODD));
		courseService.create(dto(semesterId, "双周", today, 5, 6, 1, 18, CourseTime.WEEK_TYPE_EVEN));

		assertThat(courseService.listOnDate(WorkbenchTime.today()))
				.extracting(CourseVO::name)
				.containsExactly("每周", "单周");
	}

	/** 今天不在任何学期内（寒暑假、或者还没建学期）时返回空列表，而不是报错 */
	@Test
	@DisplayName("今天不属于任何学期时返回空列表")
	void listOnDateReturnsEmptyWhenNoSemesterCoversToday() {
		// 学期下周才开始 —— 今天在它之前
		semester(THIS_MONDAY.plusWeeks(1), 18);

		assertThat(courseService.listOnDate(WorkbenchTime.today())).isEmpty();
		// 一堂课都没排过，也返回空列表
		assertThat(courseService.listOnDate(THIS_MONDAY.minusWeeks(5))).isEmpty();
	}

	/**
	 * 学期的最后一天仍然算在学期内（闭区间）。
	 *
	 * <p>写成开区间的话，学期最后一天上的课会从首页消失 ——
	 * 而那正好是期末最后一节课，最不该漏的一次。
	 *
	 * <p>顺带确认次日就出了学期：边界的两侧分别验一次，
	 * 否则"一直返回有课"的实现也能通过前半条。
	 */
	@Test
	@DisplayName("学期的最后一天算在学期内，次日不算")
	void listOnDateIncludesLastDayOfSemester() {
		// 3 周的学期：第 1 周的周一是 startDate，最后一天是 startDate + 20 天（周日）
		LocalDate startDate = THIS_MONDAY.minusWeeks(2);
		LocalDate lastDay = startDate.plusDays(3L * 7 - 1);
		assertThat(lastDay.getDayOfWeek()).isEqualTo(DayOfWeek.SUNDAY);

		Long semesterId = semester(startDate, 3);
		courseService.create(dto(semesterId, "最后一节课", 7, 1, 2, 1, 3, CourseTime.WEEK_TYPE_EVERY));

		assertThat(courseService.listOnDate(lastDay))
				.extracting(CourseVO::name)
				.containsExactly("最后一节课");
		assertThat(courseService.listOnDate(lastDay.plusDays(1))).isEmpty();
	}

	/** 课表接口返回的顺序是前端直接铺网格用的，因此由后端定死并钉住 */
	@Test
	@DisplayName("课表先按星期、再按节次排序")
	void listBySemesterIsOrderedByDayThenSection() {
		Long semesterId = semester();
		courseService.create(dto(semesterId, "周三第 5 节", 3, 5, 6, 1, 18, 0));
		courseService.create(dto(semesterId, "周一第 3 节", 1, 3, 4, 1, 18, 0));
		courseService.create(dto(semesterId, "周一第 1 节", 1, 1, 2, 1, 18, 0));

		assertThat(courseService.listBySemester(semesterId))
				.extracting(CourseVO::name)
				.containsExactly("周一第 1 节", "周一第 3 节", "周三第 5 节");
	}

	// ---------- 范围校验 ----------

	@Test
	@DisplayName("星期几超出 1-7 报 400")
	void createRejectsDayOfWeekOutOfRange() {
		Long semesterId = semester();

		for (int day : new int[] { 0, 8, -1 }) {
			assertThatThrownBy(() -> courseService.create(weekly(semesterId, "越界的课", day)))
					.isInstanceOf(BusinessException.class)
					.hasMessageContaining("星期");
		}
	}

	@Test
	@DisplayName("节次超出 1-6 报 400")
	void createRejectsSectionOutOfRange() {
		Long semesterId = semester();

		assertThatThrownBy(() -> courseService.create(dto(semesterId, "越界的课", 1, 0, 2, 1, 18, 0)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("节次");
		assertThatThrownBy(() -> courseService.create(dto(semesterId, "越界的课", 1, 5, 7, 1, 18, 0)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("节次");
	}

	@Test
	@DisplayName("开始节次晚于结束节次报 400")
	void createRejectsReversedSections() {
		Long semesterId = semester();

		assertThatThrownBy(() -> courseService.create(dto(semesterId, "颠倒的课", 1, 4, 2, 1, 18, 0)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("节次");
	}

	/**
	 * 周次的上界是**所属学期的总周数**，不是 {@code CourseTime.MAX_TOTAL_WEEKS}。
	 *
	 * <p>这是"跨表"的那一类校验：18 周的学期里排一门"到第 19 周"的课，
	 * 若只按全局上限 60 判就放行了，那条记录会进库、却永远不出现在课表的
	 * 任何一格上 —— 它没被删，只是看不见了。
	 *
	 * <p>断言里带上学期的周数（18），因为提示得说清"不能超过几周"，
	 * 否则用户只知道被拒了。
	 */
	@Test
	@DisplayName("结束周超出学期总周数报 400")
	void createRejectsWeekBeyondSemesterTotalWeeks() {
		Long semesterId = semester(THIS_MONDAY, 18);

		assertThatThrownBy(() -> courseService.create(dto(semesterId, "越界的课", 1, 1, 2, 1, 19, 0)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("18 周");
	}

	@Test
	@DisplayName("起始周早于 1、或晚于结束周，报 400")
	void createRejectsBadWeekRange() {
		Long semesterId = semester();

		assertThatThrownBy(() -> courseService.create(dto(semesterId, "越界的课", 1, 1, 2, 0, 18, 0)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("起始周");
		assertThatThrownBy(() -> courseService.create(dto(semesterId, "颠倒的课", 1, 1, 2, 10, 3, 0)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("起始周");
	}

	@Test
	@DisplayName("周类型不是 0/1/2 时报 400")
	void createRejectsInvalidWeekType() {
		Long semesterId = semester();

		assertThatThrownBy(() -> courseService.create(dto(semesterId, "越界的课", 1, 1, 2, 1, 18, 9)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("周类型");
	}

	/** 不传周类型按"每周"处理 —— 列有 DEFAULT 0，但实体上那份得自己填，否则 INSERT 写 null */
	@Test
	@DisplayName("不传周类型时按每周处理")
	void createDefaultsWeekTypeToEvery() {
		Long semesterId = semester();

		CourseVO created = courseService.create(dto(semesterId, "默认周类型", 1, 1, 2, 1, 18, null));

		assertThat(created.weekType()).isEqualTo(CourseTime.WEEK_TYPE_EVERY);
	}

	// ---------- 写路径的响应形状 ----------

	/**
	 * PUT 是全量替换，清空老师 / 地点必须**真的清掉**。
	 *
	 * <p>传 null 的话 MyBatis-Plus 会把那个字段整条跳过，旧值原封不动留在库里 ——
	 * 界面上看着已清空，刷新一下又回来了。所以 Service 把空值落成空串。
	 */
	@Test
	@DisplayName("清空老师与地点会真的写进库里，取回时是空串而不是 null")
	void updateClearsTeacherAndLocation() {
		Long semesterId = semester();
		Long id = courseService.create(dto(semesterId, "高等数学", 1, 1, 2, 1, 18, 0)).id();

		// 老师 / 地点传 null —— 前端清空输入框就是不发这两个字段
		courseService.update(id, new CourseDTO(semesterId, "高等数学", null, null,
				1, 1, 2, 1, 18, CourseTime.WEEK_TYPE_EVERY));

		// 既看返回值，也直接查库 —— 只看返回值的话，一个"只在内存里改了"的实现也能通过
		CourseVO readBack = courseService.listBySemester(semesterId).get(0);
		assertThat(readBack.teacher()).isEmpty();
		assertThat(readBack.location()).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `teacher` FROM `wb_course` WHERE `id` = ?", String.class, id)).isEmpty();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `location` FROM `wb_course` WHERE `id` = ?", String.class, id)).isEmpty();
	}

	/** 新增后就该能查出来，且各字段与传入的一致 —— 写接口回读一次的回归 */
	@Test
	@DisplayName("新增返回的对象与随后查到的完全一致")
	void createReturnMatchesSubsequentRead() {
		Long semesterId = semester();

		CourseVO created = courseService.create(dto(semesterId, "线性代数", 4, 3, 4, 5, 12, 2));
		CourseVO readBack = courseService.listBySemester(semesterId).get(0);

		assertThat(readBack).isEqualTo(created);
		assertThat(created.id()).isNotNull();
		assertThat(created.weekType()).isEqualTo(CourseTime.WEEK_TYPE_EVEN);
	}

	/** 修改后按新值算，旧的周次范围不再生效 —— 确认更新真的落了库 */
	@Test
	@DisplayName("改过周次之后按新范围判断今天有没有课")
	void updateTakesEffectOnTodayQuery() {
		Long semesterId = semester();
		int today = WorkbenchTime.today().getDayOfWeek().getValue();
		Long id = courseService.create(weekly(semesterId, "高等数学", today)).id();

		// 改成从第 2 周才开始 —— 本周是第 1 周，于是今天没有这门课了
		courseService.update(id, dto(semesterId, "高等数学", today, 1, 2, 2, 18,
				CourseTime.WEEK_TYPE_EVERY));

		assertThat(courseService.listOnDate(WorkbenchTime.today())).isEmpty();
	}

	@Test
	@DisplayName("删掉不存在的课程报 404")
	void deleteRejectsUnknownCourse() {
		assertThatThrownBy(() -> courseService.delete(-1L))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("课程不存在");
	}

	@Test
	@DisplayName("不存在的学期 id 建课报 404")
	void createRejectsUnknownSemester() {
		assertThatThrownBy(() -> courseService.create(weekly(-1L, "孤儿课", 1)))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("学期不存在");
	}

}
