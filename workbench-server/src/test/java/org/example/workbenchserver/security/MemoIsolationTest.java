package org.example.workbenchserver.security;

import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.entity.Memo;
import org.example.workbenchserver.mapper.MemoMapper;
import org.example.workbenchserver.service.MemoService;
import org.example.workbenchserver.vo.MemoVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code wb_memo} 的数据隔离回归测试。
 *
 * <p>与 {@link PlanTaskIsolationTest} 同一套思路：数据用原生 JDBC 带显式
 * {@code user_id} 种入（绕开 MyBatis），再让 Mapper / Service 去读改写删。
 * 这样拦截器一旦失效，失败点落在**断言**上而不是插入阶段。
 *
 * <p>搜索路径单独钉了一条，见 {@link #searchDoesNotLeakOtherUsersRecords()} ——
 * 那是唯一一条 WHERE 由代码拼出来、而不是简单等值匹配的查询。
 *
 * <p>需要真实 MySQL，且 {@code wb_memo} 表已由 {@code db/schema.sql} 建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class MemoIsolationTest {

	/**
	 * 远超真实用户规模的合成 id，因此可以按 user_id 精确清理。
	 * 绝不能用 TRUNCATE —— 这张表在开发库里是有真数据的。
	 */
	private static final long USER_A = 3001L;

	private static final long USER_B = 3002L;

	@Autowired
	private MemoMapper memoMapper;

	@Autowired
	private MemoService memoService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUsers() {
		jdbcTemplate.update("DELETE FROM `wb_memo` WHERE `user_id` IN (?, ?)", USER_A, USER_B);
		UserContext.clear();
	}

	/** 直接插库，user_id 由本方法显式指定 —— 这正是"绕过隔离机制"的角度 */
	private Long seed(long userId, String title, String content) {
		jdbcTemplate.update(
				"INSERT INTO `wb_memo` (`user_id`, `title`, `content`) VALUES (?, ?, ?)",
				userId, title, content);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_memo` WHERE `user_id` = ? AND `title` = ?",
				Long.class, userId, title);
	}

	/** 绕过 Mapper 直接读库，用来断言"库里到底存成什么样" */
	private String rawTitleOf(Long id) {
		return jdbcTemplate.queryForObject(
				"SELECT `title` FROM `wb_memo` WHERE `id` = ?", String.class, id);
	}

	@Test
	@DisplayName("插入时自动补上当前用户，业务代码无从指定归属")
	void insertAutoFillsCurrentUser() {
		UserContext.set(USER_A);

		// 刻意不设归属 —— Memo 上压根没有 userId 属性，想设也设不了
		Memo memo = new Memo();
		memo.setTitle("a-1");
		memo.setContent("");
		memoMapper.insert(memo);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `user_id` FROM `wb_memo` WHERE `title` = ?", Long.class, "a-1"))
				.isEqualTo(USER_A);
	}

	/**
	 * 分页的隔离要连 {@code total} 一起看。
	 *
	 * <p>分页插件会把原查询包成两条 SQL：一条 {@code COUNT(*)}、一条带 LIMIT 的查询。
	 * 拦截器若只作用于其中一条，就会出现"列表里只有自己的记录、总数却是全库的"——
	 * 页面上表现为翻到第二页就空了，比直接泄露更让人摸不着头脑。
	 */
	@Test
	@DisplayName("分页只返回当前用户的记录，total 也只是自己的条数")
	void pagingOnlyReturnsCurrentUserRows() {
		seed(USER_A, "a-1", "正文一");
		seed(USER_A, "a-2", "正文二");
		seed(USER_B, "b-1", "正文三");

		UserContext.set(USER_A);
		PageResult<MemoVO> page = memoService.page(1, 10, null);

		// 库里共 3 行。拦截器一旦失效，这里会是 3
		assertThat(page.total()).isEqualTo(2);
		assertThat(page.records())
				.extracting(MemoVO::title)
				.containsExactlyInAnyOrder("a-1", "a-2");

		UserContext.set(USER_B);
		assertThat(memoService.page(1, 10, null).total()).isEqualTo(1);
	}

	/**
	 * 搜索路径的隔离。这是本模块唯一一条 WHERE 由代码拼出来的查询
	 * （其余都是等值匹配或主键查），也是最值得单独钉的一条。
	 *
	 * <p>这里原本还写着一句"OR 没套括号就会泄露，因为 AND 优先级高于 OR"。
	 * <b>那句话是错的</b>，写的时候只是推理，没验证：MyBatis-Plus 的
	 * {@code NormalSegmentList.childrenSqlSegment()} 无条件把整段条件包进括号
	 * （3.5.1 起如此），所以拼出来的一直是
	 * {@code AND (title LIKE ? OR content LIKE ?) AND user_id = ?}。
	 *
	 * <p>是**变异测试**发现的：把 {@code MemoServiceImpl} 里的 {@code and(...)}
	 * 拆成平的 {@code like(...).or().like(...)} 之后，这条用例照样全绿 ——
	 * 说明它并不能区分二者。用例本身留着（它确实在断言隔离：拦截器失效、
	 * 或者 {@code like} 匹配错了字段，都会被它逮住），但别再把它当成
	 * "括号之争"的守卫；那道括号由库负责，见 {@code MemoServiceImpl#page} 的注释。
	 */
	@Test
	@DisplayName("搜索不会带出别人的备忘录")
	void searchDoesNotLeakOtherUsersRecords() {
		seed(USER_A, "会议纪要 A", "正文里没有关键词");
		seed(USER_B, "会议纪要 B", "正文里也没有关键词");

		UserContext.set(USER_A);
		PageResult<MemoVO> page = memoService.page(1, 10, "会议纪要");

		assertThat(page.total()).isEqualTo(1);
		assertThat(page.records())
				.extracting(MemoVO::title)
				.containsExactly("会议纪要 A");
	}

	/**
	 * 上一条的"正向对照"。
	 *
	 * <p>少了它，一个**彻底坏掉**的搜索（比如关键词参数根本没接上、永远搜不到东西）
	 * 也能让上一条全绿 —— 那种情况下测试看着在防泄露，实际什么都没防。
	 * 这条保证搜索确实按正文匹配到了本人的记录。
	 */
	@Test
	@DisplayName("搜索确实能按正文匹配到自己的记录")
	void searchMatchesOwnContent() {
		seed(USER_A, "无题", "正文里有土豆两个字");

		UserContext.set(USER_A);
		PageResult<MemoVO> page = memoService.page(1, 10, "土豆");

		assertThat(page.total()).isEqualTo(1);
		assertThat(page.records()).extracting(MemoVO::title).containsExactly("无题");
	}

	@Test
	@DisplayName("拿别人的主键查不到")
	void selectByIdCannotReachAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1", "正文");

		// 主键是连续自增的，猜到别人的 id 毫无难度 —— 这是最典型的越权入口
		UserContext.set(USER_B);
		assertThat(memoMapper.selectById(idOwnedByA)).isNull();
	}

	@Test
	@DisplayName("改不动别人的记录")
	void updateCannotModifyAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1", "正文");

		UserContext.set(USER_B);
		Memo forged = new Memo();
		forged.setId(idOwnedByA);
		forged.setTitle("被人改了");

		int affected = memoMapper.updateById(forged);

		// 拦截器会给 UPDATE 也补上 user_id，所以 WHERE 匹配不到任何行
		assertThat(affected).isZero();
		assertThat(rawTitleOf(idOwnedByA)).isEqualTo("a-1");
	}

	@Test
	@DisplayName("删不掉别人的记录")
	void deleteCannotRemoveAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "a-1", "正文");

		UserContext.set(USER_B);
		assertThat(memoMapper.deleteById(idOwnedByA)).isZero();

		// 逻辑删除走的是 UPDATE ... SET deleted = 时间戳，确认它没被改脏
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_memo` WHERE `id` = ?", Long.class, idOwnedByA))
				.isZero();
	}

	@Test
	@DisplayName("越界的分页参数被夹回合法范围")
	void paginationParamsAreClamped() {
		seed(USER_A, "a-1", "正文");

		UserContext.set(USER_A);
		// pageSize 传负数时不能把整表捞出来，pageNum 传 0 也不能把 0 回显给前端
		PageResult<MemoVO> page = memoService.page(0, -1, null);

		assertThat(page.pageNum()).isEqualTo(1);
		assertThat(page.pageSize()).isEqualTo(10);
		assertThat(page.records()).hasSize(1);
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
		Long id = seed(USER_A, "a-1", "正文");

		UserContext.set(USER_A);
		assertThat(memoMapper.deleteById(id)).isEqualTo(1);

		assertThat(memoMapper.selectById(id)).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_memo` WHERE `id` = ?", Long.class, id))
				.isNotZero();
	}

}
