package org.example.workbenchserver.security;

import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.entity.Expense;
import org.example.workbenchserver.mapper.ExpenseMapper;
import org.example.workbenchserver.service.ExpenseService;
import org.example.workbenchserver.vo.CategorySummaryVO;
import org.example.workbenchserver.vo.ExpenseVO;
import org.example.workbenchserver.vo.MonthSummaryVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code wb_expense} 的数据隔离回归测试。
 *
 * <p>与 {@link MemoIsolationTest} 同一套思路：数据用原生 JDBC 带显式
 * {@code user_id} 种入（绕开 MyBatis），再让 Mapper / Service 去读改写删。
 * 这样拦截器一旦失效，失败点落在**断言**上而不是插入阶段。
 *
 * <p>本类多钉两条路径：{@code summaryByCategory} 与 {@code summaryByMonth}。
 * 它们是全仓库头一次出现"带 {@code GROUP BY} 的查询" —— 隔离条件同样由拦截器
 * 注入到 {@code WHERE} 里，但聚合语句的改写路径和普通查询不是同一条，
 * 值得单独确认。**这两条一旦失效，漏的不是"能看到别人的记录"这么显眼**：
 * 饼图上只会多出一块、折线图上只会高一点，没有任何报错。
 *
 * <p><b>测试数据一律种在 1990 年</b>（{@code TEST_YEAR}），且汇总用例都带年份过滤。
 * 分类是预置的六个，没法用"造一个只有测试在用的分类"来隔离；
 * 而开发库里的 {@code wb_expense} 迟早会有真实数据（手工记账时就会写进去）。
 * 用一个人绝不会记账的年份，汇总断言才是自洽的 —— 否则某天往库里记一笔，
 * 这些用例就会莫名其妙地红，而红的原因和被测逻辑毫无关系。
 *
 * <p>需要真实 MySQL，且 {@code wb_expense} 表已由 {@code db/schema.sql} 建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class ExpenseIsolationTest {

	/**
	 * 远超真实用户规模的合成 id，因此可以按 user_id 精确清理。
	 * 绝不能用 TRUNCATE —— 这张表在开发库里迟早是有真数据的。
	 */
	private static final long USER_A = 4001L;

	private static final long USER_B = 4002L;

	/** 测试数据专用的年份，理由见类注释 */
	private static final int TEST_YEAR = 1990;

	@Autowired
	private ExpenseMapper expenseMapper;

	@Autowired
	private ExpenseService expenseService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUsers() {
		jdbcTemplate.update("DELETE FROM `wb_expense` WHERE `user_id` IN (?, ?)", USER_A, USER_B);
		UserContext.clear();
	}

	/** 直接插库，user_id 由本方法显式指定 —— 这正是"绕过隔离机制"的角度 */
	private Long seed(long userId, String amount, String category, LocalDate date) {
		jdbcTemplate.update(
				"INSERT INTO `wb_expense` (`user_id`, `amount`, `category`, `expense_date`, `remark`) "
						+ "VALUES (?, ?, ?, ?, '')",
				userId, new BigDecimal(amount), category, date);
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_expense` WHERE `user_id` = ? AND `expense_date` = ? AND `amount` = ?",
				Long.class, userId, date, new BigDecimal(amount));
	}

	private static LocalDate day(int month, int dayOfMonth) {
		return LocalDate.of(TEST_YEAR, month, dayOfMonth);
	}

	@Test
	@DisplayName("插入时自动补上当前用户，业务代码无从指定归属")
	void insertAutoFillsCurrentUser() {
		UserContext.set(USER_A);

		// 刻意不设归属 —— Expense 上压根没有 userId 属性，想设也设不了
		Expense expense = new Expense();
		expense.setAmount(new BigDecimal("12.34"));
		expense.setCategory("餐饮");
		expense.setExpenseDate(day(3, 1));
		expense.setRemark("");
		expenseMapper.insert(expense);

		assertThat(jdbcTemplate.queryForObject(
				"SELECT `user_id` FROM `wb_expense` WHERE `id` = ?", Long.class, expense.getId()))
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
		seed(USER_A, "10.00", "餐饮", day(3, 1));
		seed(USER_A, "20.00", "交通", day(3, 2));
		seed(USER_B, "30.00", "餐饮", day(3, 3));

		UserContext.set(USER_A);
		PageResult<ExpenseVO> page = expenseService.page(1, 10, null, null, null);

		// 库里共 3 行。拦截器一旦失效，这里会是 3
		assertThat(page.total()).isEqualTo(2);
		assertThat(page.records())
				.extracting(ExpenseVO::category)
				.containsExactlyInAnyOrder("餐饮", "交通");

		UserContext.set(USER_B);
		assertThat(expenseService.page(1, 10, null, null, null).total()).isEqualTo(1);
	}

	/**
	 * 按分类汇总的隔离。**本类最要紧的一条。**
	 *
	 * <p>两个用户都有"餐饮"，所以断言不能只看"有没有这一行"，必须看**金额对不对**：
	 * 拦截器一旦失效，A 看到的餐饮合计会变成 A + B 之和，而分类名仍然是"餐饮"，
	 * 只看分类名的断言完全发现不了。
	 */
	@Test
	@DisplayName("按分类汇总只统计自己的记录")
	void categorySummaryOnlyAggregatesOwnRows() {
		seed(USER_A, "100.00", "餐饮", day(1, 5));
		seed(USER_A, "50.50", "餐饮", day(2, 5));
		seed(USER_A, "20.00", "交通", day(2, 6));
		// B 的餐饮金额刻意取一个大数：混进来时合计会明显对不上
		seed(USER_B, "9999.00", "餐饮", day(1, 5));

		UserContext.set(USER_A);
		List<CategorySummaryVO> summary =
				expenseService.summaryByCategory(LocalDate.of(TEST_YEAR, 1, 1), LocalDate.of(TEST_YEAR, 12, 31));

		assertThat(summary).hasSize(2);
		// 金额从大到小
		assertThat(summary).extracting(CategorySummaryVO::category)
				.containsExactly("餐饮", "交通");
		assertThat(summary.get(0).amount()).isEqualByComparingTo("150.50");
		assertThat(summary.get(1).amount()).isEqualByComparingTo("20.00");
	}

	/**
	 * 按月汇总的隔离。
	 *
	 * <p>与上一条同样的道理，而且这里更容易漏：{@code summaryByMonth} 会把
	 * 12 个月**全部**补零返回，所以"某个月金额不为 0"这件事本身
	 * 是它正常工作时的常态 —— 断言必须落到具体是哪个月、具体多少钱。
	 */
	@Test
	@DisplayName("按月汇总只统计自己的记录")
	void monthSummaryOnlyAggregatesOwnRows() {
		seed(USER_A, "100.00", "餐饮", day(1, 5));
		seed(USER_A, "23.45", "交通", day(1, 20));
		seed(USER_B, "9999.00", "购物", day(1, 5));

		UserContext.set(USER_A);
		List<MonthSummaryVO> summary = expenseService.summaryByMonth(TEST_YEAR);

		assertThat(summary).hasSize(12);
		assertThat(summary.get(0).month()).isEqualTo("1990-01");
		assertThat(summary.get(0).amount()).isEqualByComparingTo("123.45");
		// 只种了 1 月的数据，其余 11 个月必须是 0
		assertThat(summary.subList(1, 12))
				.allSatisfy(m -> assertThat(m.amount()).isEqualByComparingTo("0.00"));
	}

	/**
	 * 逻辑删除的记录不能进汇总。
	 *
	 * <p>这条与"隔离"并列，因为它们的失效方式一样安静：{@code @TableLogic} 的条件
	 * 若没被拼进那条 {@code GROUP BY} 语句，被删掉的账目会继续算在饼图里，
	 * 且没有任何报错 —— 用户只会觉得"这个月怎么花了这么多"。
	 */
	@Test
	@DisplayName("已删除的记录不计入汇总")
	void summaryExcludesSoftDeletedRows() {
		Long kept = seed(USER_A, "100.00", "餐饮", day(4, 1));
		seed(USER_A, "300.00", "餐饮", day(4, 2));

		UserContext.set(USER_A);
		expenseService.delete(kept);

		// 只能剩一条。要防的不只是"删掉的还在算"，还有"整个分类都没了"——
		// 后者会让金额变成 0 或整条消失，singleElement 一并挡住
		assertThat(expenseService.summaryByCategory(LocalDate.of(TEST_YEAR, 4, 1), LocalDate.of(TEST_YEAR, 4, 30)))
				.singleElement()
				.satisfies(row -> assertThat(row.amount()).isEqualByComparingTo("300.00"));

		assertThat(expenseService.summaryByMonth(TEST_YEAR).get(3).amount())
				.isEqualByComparingTo("300.00");
	}

	@Test
	@DisplayName("拿别人的主键查不到")
	void selectByIdCannotReachAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "10.00", "餐饮", day(5, 1));

		// 主键是连续自增的，猜到别人的 id 毫无难度 —— 这是最典型的越权入口
		UserContext.set(USER_B);
		assertThat(expenseMapper.selectById(idOwnedByA)).isNull();
	}

	@Test
	@DisplayName("改不动别人的记录")
	void updateCannotModifyAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "10.00", "餐饮", day(6, 1));

		UserContext.set(USER_B);
		Expense forged = new Expense();
		forged.setId(idOwnedByA);
		forged.setAmount(new BigDecimal("8888.00"));
		forged.setCategory("购物");

		int affected = expenseMapper.updateById(forged);

		// 拦截器会给 UPDATE 也补上 user_id，所以 WHERE 匹配不到任何行
		assertThat(affected).isZero();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `category` FROM `wb_expense` WHERE `id` = ?", String.class, idOwnedByA))
				.isEqualTo("餐饮");
	}

	@Test
	@DisplayName("删不掉别人的记录")
	void deleteCannotRemoveAnotherUsersRow() {
		Long idOwnedByA = seed(USER_A, "10.00", "餐饮", day(7, 1));

		UserContext.set(USER_B);
		assertThat(expenseMapper.deleteById(idOwnedByA)).isZero();

		// 逻辑删除走的是 UPDATE ... SET deleted = 时间戳，确认它没被改脏
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_expense` WHERE `id` = ?", Long.class, idOwnedByA))
				.isZero();
	}

	/**
	 * 逻辑删除本身的行为，与隔离无关（拦截器失效时它照样通过）。
	 *
	 * <p>留着是因为它验证了本模块的 {@code @TableLogic} 接得对：
	 * 删除后本人查不到，但库里那行还在、{@code deleted} 变成了非 0。
	 */
	@Test
	@DisplayName("逻辑删除后本人也查不到，但数据仍在库里")
	void logicDeleteHidesRowFromOwnerToo() {
		Long id = seed(USER_A, "10.00", "餐饮", day(8, 1));

		UserContext.set(USER_A);
		assertThat(expenseMapper.deleteById(id)).isEqualTo(1);

		assertThat(expenseMapper.selectById(id)).isNull();
		assertThat(jdbcTemplate.queryForObject(
				"SELECT `deleted` FROM `wb_expense` WHERE `id` = ?", Long.class, id))
				.isNotZero();
	}

	@Test
	@DisplayName("越界的分页参数被夹回合法范围")
	void paginationParamsAreClamped() {
		seed(USER_A, "10.00", "餐饮", day(9, 1));

		UserContext.set(USER_A);
		// pageSize 传负数时不能把整表捞出来，pageNum 传 0 也不能把 0 回显给前端
		PageResult<ExpenseVO> page = expenseService.page(0, -1, null, null, null);

		assertThat(page.pageNum()).isEqualTo(1);
		assertThat(page.pageSize()).isEqualTo(10);
		assertThat(page.records()).hasSize(1);
	}

}
