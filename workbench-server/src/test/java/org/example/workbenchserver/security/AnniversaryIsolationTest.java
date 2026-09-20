package org.example.workbenchserver.security;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.entity.Anniversary;
import org.example.workbenchserver.mapper.AnniversaryMapper;
import org.example.workbenchserver.service.AnniversaryService;
import org.example.workbenchserver.vo.UpcomingAnniversaryVO;
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

/**
 * {@code wb_anniversary} 的数据隔离回归测试。
 *
 * <p>与 {@link PlanTaskIsolationTest} 同一套思路：数据用原生 JDBC 带显式
 * {@code user_id} 种入（绕开 MyBatis），再让 Mapper 去读改写删。这样拦截器一旦失效，
 * 失败点落在**断言**上而不是插入阶段 —— 后者会让测试红在错误的位置，
 * 读改写删的越权断言根本没跑过。
 *
 * <p>最后一个用例额外验一件本模块特有的事：{@code upcoming} 是先把当前用户的
 * **全部记录**取出来、再在 Java 里筛的，SQL 里没有任何 WHERE 条件。
 * 隔离全靠拦截器，所以这条路径更值得单独钉一下。
 *
 * <p>需要真实 MySQL，且 {@code wb_anniversary} 表已由 {@code db/schema.sql} 建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class AnniversaryIsolationTest {

	/**
	 * 用的是远超真实用户规模的合成 id，因此可以按 user_id 精确清理。
	 *
	 * <p>本表不像 {@code wb_plan_task} 那样能拿日期圈定范围（只存月和日，
	 * 没有可用于隔离测试的日期列），所以换个思路：真实用户的自增 id 到不了 2001，
	 * 按这两个 id 删就绝不会误伤真数据。绝不能用 TRUNCATE ——
	 * 这张表在生产库里是有真数据的。
	 */
	private static final long USER_A = 2001L;

	private static final long USER_B = 2002L;

	@Autowired
	private AnniversaryMapper anniversaryMapper;

	@Autowired
	private AnniversaryService anniversaryService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUsers() {
		jdbcTemplate.update(
				"DELETE FROM `wb_anniversary` WHERE `user_id` IN (?, ?)", USER_A, USER_B);
		UserContext.clear();
	}

	/** 直接插库，user_id 由本方法显式指定 —— 这正是"绕过隔离机制"的角度 */
	private Long seed(long userId, String name, int month, int day) {
		jdbcTemplate.update(
				"INSERT INTO `wb_anniversary`"
						+ " (`user_id`, `name`, `type`, `relation`, `month`, `day`, `remind_days`, `remark`)"
						+ " VALUES (?, ?, 1, '家人', ?, ?, 7, '')",
				userId, name, month, day);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_anniversary` WHERE `user_id` = ? AND `name` = ?",
				Long.class, userId, name);
	}

	/** 绕过 Mapper 直接读库，用来断言"库里到底存成什么样" */
	private String rawNameOf(Long id) {
		return jdbcTemplate.queryForObject(
				"SELECT `name` FROM `wb_anniversary` WHERE `id` = ?", String.class, id);
	}

	private List<Anniversary> queryAll() {
		// 条件里没有任何 user_id —— 隔离全靠拦截器
		return anniversaryMapper.selectList(new LambdaQueryWrapper<>());
	}

	@Test
	@DisplayName("插入时自动补上当前用户，业务代码无从指定归属")
	void insertAutoFillsCurrentUser() {
		UserContext.set(USER_A);

		// 刻意不设归属 —— Anniversary 上压根没有 userId 属性，想设也设不了
		Anniversary anniversary = new Anniversary();
		anniversary.setName("a-1");
		anniversary.setType(1);
		anniversary.setRelation("家人");
		anniversary.setMonth(10);
		anniversary.setDay(5);
		anniversary.setRemindDays(7);
		anniversary.setRemark("");
		anniversaryMapper.insert(anniversary);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `user_id` FROM `wb_anniversary` WHERE `name` = ?", Long.class, "a-1"))
				.isEqualTo(USER_A);
	}

	@Test
	@DisplayName("查询只返回当前用户的记录")
	void selectOnlyReturnsCurrentUserRows() {
		seed(USER_A, "a-1", 10, 5);
		seed(USER_A, "a-2", 3, 8);
		seed(USER_B, "b-1", 12, 25);

		// 库里共有 3 行。拦截器一旦失效，这里会查出全部 3 条。
		UserContext.set(USER_A);
		assertThat(queryAll())
				.extracting(Anniversary::getName)
				.containsExactlyInAnyOrder("a-1", "a-2");

		UserContext.set(USER_B);
		assertThat(queryAll())
				.extracting(Anniversary::getName)
				.containsExactly("b-1");
	}

	@Test
	@DisplayName("拿别人的主键查不到")
	void selectByIdCannotReachAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1", 10, 5);

		// 主键是连续自增的，猜到别人的 id 毫无难度 —— 这是最典型的越权入口
		UserContext.set(USER_B);
		assertThat(anniversaryMapper.selectById(idOwnedByA)).isNull();
	}

	@Test
	@DisplayName("改不动别人的记录")
	void updateCannotModifyAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1", 10, 5);

		UserContext.set(USER_B);
		Anniversary forged = new Anniversary();
		forged.setId(idOwnedByA);
		forged.setName("被人改了");

		int affected = anniversaryMapper.updateById(forged);

		// 拦截器会给 UPDATE 也补上 user_id，所以 WHERE 匹配不到任何行
		assertThat(affected).isZero();
		assertThat(rawNameOf(idOwnedByA)).isEqualTo("a-1");
	}

	@Test
	@DisplayName("删不掉别人的记录")
	void deleteCannotRemoveAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1", 10, 5);

		UserContext.set(USER_B);
		assertThat(anniversaryMapper.deleteById(idOwnedByA)).isZero();

		// 逻辑删除走的是 UPDATE ... SET deleted = 时间戳，确认它没被改脏
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_anniversary` WHERE `id` = ?", Long.class, idOwnedByA))
				.isZero();
	}

	/**
	 * 本模块特有的路径：{@code upcoming} 先把当前用户的全部记录取出来，
	 * 再在 Java 里算剩余天数并筛掉没进提醒窗口的。
	 *
	 * <p>正因为筛选逻辑在 Java 里、SQL 里没有任何 WHERE，隔离**只**依赖拦截器。
	 * 所以这条用例值得单独存在：拦截器失效时，别人的生日会直接出现在你的首页提醒里。
	 */
	@Test
	@DisplayName("首页提醒不会带出别人的生日")
	void upcomingDoesNotLeakOtherUsersRecords() {
		LocalDate today = WorkbenchTime.today();
		// 把生日设成"今天"，确保它一定落在提醒窗口内（剩余 0 天 ≤ 7 天）
		seed(USER_A, "a-today", today.getMonthValue(), today.getDayOfMonth());

		UserContext.set(USER_B);
		assertThat(anniversaryService.upcoming()).isEmpty();

		UserContext.set(USER_A);
		// record 的访问器是 name()，不是 getName() —— 别照抄上面实体那几处
		assertThat(anniversaryService.upcoming())
				.extracting(UpcomingAnniversaryVO::name)
				.containsExactly("a-today");
	}

	/**
	 * 逻辑删除本身的行为，与隔离无关（拦截器失效时它照样通过）。
	 *
	 * <p>留着是因为它验证了 {@code deleted} 用 **BIGINT 时间戳** 这套方案确实通：
	 * 删除后本人查不到，但库里那行还在、{@code deleted} 变成了非 0。
	 */
	@Test
	@DisplayName("逻辑删除后本人也查不到，但数据仍在库里")
	void logicDeleteHidesRowFromOwnerToo() {
		Long id = seed(USER_A, "a-1", 10, 5);

		UserContext.set(USER_A);
		assertThat(anniversaryMapper.deleteById(id)).isEqualTo(1);

		assertThat(anniversaryMapper.selectById(id)).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_anniversary` WHERE `id` = ?", Long.class, id))
				.isNotZero();
	}

}
