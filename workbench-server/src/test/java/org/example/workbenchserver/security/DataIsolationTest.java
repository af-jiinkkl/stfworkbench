package org.example.workbenchserver.security;

import org.example.workbenchserver.isolation.IsolationProbe;
import org.example.workbenchserver.isolation.IsolationProbeConfig;
import org.example.workbenchserver.isolation.IsolationProbeMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证数据隔离确实生效 —— 这是整个项目的安全底线
 * （docs/接口清单.md §1.6、docs/需求说明.md §3.0）。
 *
 * <p>要验证的核心命题：**业务代码一行 user_id 条件都不写，
 * 查询结果也必须只包含当前用户的数据**。所以下面的查询全部传 {@code null}
 * 作为条件 —— 一旦拦截器失效，它们会返回所有用户的数据，测试立刻失败。
 *
 * <p>本测试需要真实 MySQL（连接信息同 application.yml）。
 * 跑之前需设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
@Import(IsolationProbeConfig.class)
class DataIsolationTest {

	private static final long USER_A = 1001L;

	private static final long USER_B = 1002L;

	@Autowired
	private IsolationProbeMapper probeMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void createProbeTable() {
		jdbcTemplate.execute("""
				CREATE TABLE IF NOT EXISTS `wb_isolation_probe` (
				  `id`      BIGINT      NOT NULL AUTO_INCREMENT,
				  `user_id` BIGINT      NOT NULL,
				  `content` VARCHAR(50) NOT NULL,
				  PRIMARY KEY (`id`)
				) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci
				""");
		jdbcTemplate.execute("TRUNCATE TABLE `wb_isolation_probe`");
	}

	@AfterEach
	void clearContext() {
		// 测试跑在同一个线程上，不清的话下个用例会读到上个用例的用户身份
		UserContext.clear();
	}

	@Test
	void insertAutoFillsCurrentUser() {
		UserContext.set(USER_A);

		// 刻意不调 setUserId —— 值应当由拦截器自动补上
		probeMapper.insert(new IsolationProbe("a-1"));
		probeMapper.insert(new IsolationProbe("a-2"));

		List<Long> ownerIds = jdbcTemplate.queryForList(
				"SELECT DISTINCT user_id FROM wb_isolation_probe", Long.class);

		assertThat(ownerIds).containsExactly(USER_A);
	}

	@Test
	void selectOnlyReturnsCurrentUserRows() {
		UserContext.set(USER_A);
		probeMapper.insert(new IsolationProbe("a-1"));
		probeMapper.insert(new IsolationProbe("a-2"));

		UserContext.set(USER_B);
		probeMapper.insert(new IsolationProbe("b-1"));

		// 数据库里此刻共 3 行。下面查询条件传 null（即 WHERE 1=1），
		// 若隔离失效会把 3 行全查出来。
		UserContext.set(USER_A);
		List<IsolationProbe> rowsForA = probeMapper.selectList(null);
		assertThat(rowsForA).hasSize(2);
		assertThat(rowsForA).allSatisfy(row -> assertThat(row.getUserId()).isEqualTo(USER_A));

		UserContext.set(USER_B);
		List<IsolationProbe> rowsForB = probeMapper.selectList(null);
		assertThat(rowsForB).hasSize(1);
		assertThat(rowsForB.get(0).getContent()).isEqualTo("b-1");
	}

	@Test
	void selectByIdCannotReachAnotherUsersRow() {
		UserContext.set(USER_A);
		probeMapper.insert(new IsolationProbe("a-1"));

		Long idOwnedByA = probeMapper.selectList(null).get(0).getId();

		// 换成 B，拿着 A 的主键去查。主键是猜得到的连续整数，
		// 这是最典型的越权场景 —— 必须查不到。
		UserContext.set(USER_B);
		assertThat(probeMapper.selectById(idOwnedByA)).isNull();
	}

}
