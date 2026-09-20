package org.example.workbenchserver.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.entity.PlanTask;
import org.example.workbenchserver.mapper.PlanTaskMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code wb_plan_task} 的数据隔离回归测试。
 *
 * <p>{@link DataIsolationTest} 用一张临时探针表证明了拦截器本身有效；
 * 本测试补的是另一件事：**真实的实体与 Mapper 接上拦截器之后是否也有效**。
 * 探针表证明机制可用，本测试证明本模块确实用上了它。
 *
 * <h2>为什么数据用原生 JDBC 种，而不是用 Mapper 插</h2>
 *
 * 最初这些用例是用 {@code planTaskMapper.insert()} 准备数据的。做变异验证
 * （把 {@code wb_plan_task} 加进 {@code TABLES_WITHOUT_USER_ID}）时发现：
 * 六个用例确实全红，但报的都是 {@code DataIntegrityViolation} ——
 * 因为 {@code user_id} 是 NOT NULL，拦截器不再补值，**插入这一步就失败了**。
 *
 * <p>也就是说查询压根没被执行，select / update / delete 那几条越权断言
 * 从未被触及。测试是红的，却红在了错误的位置 —— 它能发现"机制被关掉了"，
 * 但无法说明"读改写删各自越权时能否挡住"。
 *
 * <p>所以改成用原生 JDBC 带显式 {@code user_id} 种数据（绕开 MyBatis，
 * 不受拦截器影响），再让 Mapper 去读改写删。这样一旦拦截器失效，
 * 失败点就落在**断言**上：查别人的数据会查出来、改别人的行会改动成功。
 *
 * <h2>与其他测试的边界</h2>
 *
 * 前 5 个用例验证隔离，{@link #logicDeleteHidesRowFromOwnerToo} 验证的是
 * 逻辑删除本身（它即使拦截器失效也会通过，因为逻辑删除与租户插件无关）。
 *
 * <p>需要真实 MySQL，且 {@code wb_plan_task} 表已由 {@code db/schema.sql} 建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class PlanTaskIsolationTest {

	private static final long USER_A = 2001L;

	private static final long USER_B = 2002L;

	/** 2099 年，真实数据不可能落在这天，用它把测试数据与生产数据隔开 */
	private static final LocalDate TEST_DATE = LocalDate.of(2099, 1, 1);

	@Autowired
	private PlanTaskMapper planTaskMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestDate() {
		// 走原生 JDBC 而非 Mapper：这里要的是"不管谁的数据都清掉"，
		// 而 MyBatis 的删除会被拦截器加上 user_id 条件，反而清不干净。
		// 同时用测试日期圈定范围，绝不误伤真实任务 —— 本表在生产库里有真数据，
		// 不能像探针表那样 TRUNCATE。
		jdbcTemplate.update("DELETE FROM `wb_plan_task` WHERE `plan_date` = ?", TEST_DATE);
		UserContext.clear();
	}

	/** 直接插库，user_id 由本方法显式指定 —— 这正是"绕过隔离机制"的角度 */
	private Long seed(long userId, String content) {
		jdbcTemplate.update(
				"INSERT INTO `wb_plan_task` (`user_id`, `plan_date`, `content`, `completed`, `sort_order`)"
						+ " VALUES (?, ?, ?, 0, 0)",
				userId, TEST_DATE, content);
		// 回查**带上 user_id**：只按 (日期, 内容) 找的话，库里任何一条同名同日的
		// 记录都会撞进来 —— 而这几个字段都不唯一，本类清理时又只删自己那两个
		// 测试用户的（见 cleanTestUsers）。撞上的表现是 queryForObject 抛
		// IncorrectResultSize，跟这里要验的隔离毫无关系
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_plan_task` WHERE `user_id` = ? AND `plan_date` = ? AND `content` = ?",
				Long.class, userId, TEST_DATE, content);
	}

	/** 绕过 Mapper 直接读库，用来断言"库里到底存成什么样" */
	private String rawContentOf(Long id) {
		return jdbcTemplate.queryForObject(
				"SELECT `content` FROM `wb_plan_task` WHERE `id` = ?", String.class, id);
	}

	private List<PlanTask> queryTestDate() {
		// 条件里只有日期，**没有 user_id** —— 隔离全靠拦截器
		return planTaskMapper.selectList(
				new LambdaQueryWrapper<PlanTask>().eq(PlanTask::getPlanDate, TEST_DATE));
	}

	@Test
	void insertAutoFillsCurrentUser() {
		UserContext.set(USER_A);
		// 刻意不设归属 —— PlanTask 上压根没有 userId 属性，想设也设不了
		PlanTask task = new PlanTask();
		task.setPlanDate(TEST_DATE);
		task.setContent("a-1");
		task.setCompleted(0);
		task.setSortOrder(0);
		planTaskMapper.insert(task);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `user_id` FROM `wb_plan_task` WHERE `content` = ?", Long.class, "a-1"))
				.isEqualTo(USER_A);
	}

	@Test
	void selectOnlyReturnsCurrentUserRows() {
		seed(USER_A, "a-1");
		seed(USER_A, "a-2");
		seed(USER_B, "b-1");

		// 这一天共有 3 行。拦截器一旦失效，这里会查出全部 3 条。
		UserContext.set(USER_A);
		assertThat(queryTestDate())
				.extracting(PlanTask::getContent)
				.containsExactly("a-1", "a-2");

		UserContext.set(USER_B);
		assertThat(queryTestDate())
				.extracting(PlanTask::getContent)
				.containsExactly("b-1");
	}

	@Test
	void selectByIdCannotReachAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1");

		// 主键是连续自增的，猜到别人的 id 毫无难度 —— 这是最典型的越权入口
		UserContext.set(USER_B);
		assertThat(planTaskMapper.selectById(idOwnedByA)).isNull();
	}

	@Test
	void updateCannotModifyAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1");

		UserContext.set(USER_B);
		PlanTask forged = new PlanTask();
		forged.setId(idOwnedByA);
		forged.setContent("被人改了");

		int affected = planTaskMapper.updateById(forged);

		// 拦截器会给 UPDATE 也补上 user_id，所以 WHERE 匹配不到任何行
		assertThat(affected).isZero();
		assertThat(rawContentOf(idOwnedByA)).isEqualTo("a-1");
	}

	@Test
	void deleteCannotRemoveAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1");

		UserContext.set(USER_B);
		assertThat(planTaskMapper.deleteById(idOwnedByA)).isZero();

		// 逻辑删除走的是 UPDATE ... SET deleted = 时间戳，确认它没被改脏
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_plan_task` WHERE `id` = ?", Long.class, idOwnedByA))
				.isZero();
	}

	/**
	 * 逻辑删除本身的行为，与隔离无关（拦截器失效时它照样通过）。
	 *
	 * <p>留着是因为它验证了 {@code deleted} 用 **BIGINT 时间戳** 这套方案确实通：
	 * 删除后本人查不到，但库里那行还在、{@code deleted} 变成了非 0。
	 */
	@Test
	void logicDeleteHidesRowFromOwnerToo() {
		Long id = seed(USER_A, "a-1");

		UserContext.set(USER_A);
		assertThat(planTaskMapper.deleteById(id)).isEqualTo(1);

		assertThat(planTaskMapper.selectById(id)).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_plan_task` WHERE `id` = ?", Long.class, id))
				.isNotZero();
	}

}
