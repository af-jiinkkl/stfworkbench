package org.example.workbenchserver.service;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.dto.ExpenseDTO;
import org.example.workbenchserver.security.UserContext;
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
import java.time.format.DateTimeFormatter;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 消费 Service 层的行为用例。
 *
 * <p>和 {@code ExpenseIsolationTest} 分工不同：那边验"看不到别人的数据"，
 * 这边验"自己的数据长什么样"。三条主线：
 *
 * <ul>
 *   <li><b>金额的精度与写法</b> —— {@code DECIMAL} 这个选择值不值得，
 *       全靠这几条钉住。它是全仓库唯一一个"用错类型会静默出错"的字段</li>
 *   <li><b>写路径的响应形状</b> —— 与备忘录同一个坑的消费版：
 *       数据库填的值（时间戳，以及 DECIMAL(10,2) 的**小数位**）MyBatis-Plus 不会带回实体</li>
 *   <li><b>入参校验</b> —— 分类合法性、日期区间顺序</li>
 * </ul>
 *
 * <p>需要真实 MySQL，且 {@code wb_expense} 表已建好（见 db/schema.sql）。
 */
@SpringBootTest
class ExpenseServiceTest {

	/** 合成用户 id，远离真实用户规模，可按 user_id 精确清理 */
	private static final long USER_ID = 4010L;

	/** 测试数据专用年份，同 {@code ExpenseIsolationTest} 的理由：开发库里迟早会有真实账目 */
	private static final int TEST_YEAR = 1991;

	@Autowired
	private ExpenseService expenseService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUser() {
		jdbcTemplate.update("DELETE FROM `wb_expense` WHERE `user_id` = ?", USER_ID);
		UserContext.clear();
	}

	private static LocalDate day(int month, int dayOfMonth) {
		return LocalDate.of(TEST_YEAR, month, dayOfMonth);
	}

	private ExpenseDTO dto(String amount, String category, LocalDate date, String remark) {
		return new ExpenseDTO(new BigDecimal(amount), category, date, remark);
	}

	private void seed(String amount, String category, LocalDate date) {
		jdbcTemplate.update(
				"INSERT INTO `wb_expense` (`user_id`, `amount`, `category`, `expense_date`, `remark`) "
						+ "VALUES (?, ?, ?, ?, '')",
				USER_ID, new BigDecimal(amount), category, date);
	}

	/**
	 * <b>写路径返回的时间戳不能是 null。</b>
	 *
	 * <p>{@code create_time} / {@code update_time} 由数据库的
	 * {@code DEFAULT CURRENT_TIMESTAMP} 填，MyBatis-Plus 插完不会把生成的值带回实体。
	 * 备忘录那边曾经就是这么错的（POST 返回 null、紧接着 GET 却有值）。
	 */
	@Test
	@DisplayName("新增返回的创建/更新时间已填好，且是约定格式")
	void createReturnsTimestamps() {
		UserContext.set(USER_ID);

		ExpenseVO created = expenseService.create(dto("12.34", "餐饮", day(1, 1), ""));

		assertThat(created.createTime()).isNotNull();
		assertThat(created.updateTime()).isNotNull();
		assertThat(created.createTime()
				.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
				.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
	}

	/**
	 * <b>金额的小数位也归数据库定，POST 必须返回写进库之后的那份。</b>
	 *
	 * <p>这是"回读一次"在备忘录之外多出来的一个理由，而且比时间戳那条更隐蔽：
	 * 列是 {@code DECIMAL(10,2)}，传 {@code 10.5} 进去存的是 {@code 10.50}，
	 * 而实体里那份仍是 scale = 1 的 {@code 10.5}。不回读的话，
	 * POST 的响应写着 {@code 10.5}、紧接着 GET 同一个 id 却是 {@code 10.50}。
	 *
	 * <p>时间戳对不上是一眼能看出来的 null，而这个**数值相等**——
	 * 只有在 JSON 原文里才看得出两种写法，端到端跑一遍都不会觉得哪里不对。
	 *
	 * <p>断言用 {@code toPlainString()} 而不是 {@code isEqualByComparingTo}：
	 * 后者认为 {@code 10.5} 和 {@code 10.50} 相等，那就把要验的东西放过去了。
	 */
	@Test
	@DisplayName("新增返回的金额是写库后的样子：10.5 进去，10.50 出来")
	void createReturnsAmountAsStored() {
		UserContext.set(USER_ID);

		ExpenseVO created = expenseService.create(dto("10.5", "餐饮", day(1, 2), ""));

		assertThat(created.amount().toPlainString()).isEqualTo("10.50");
		// 再查一次，确认这不是 VO 上的一次性修饰，而是库里就是这样
		assertThat(expenseService.detail(created.id()).amount().toPlainString()).isEqualTo("10.50");
	}

	/**
	 * <b>改完之后 update_time 必须真的往后走。</b>
	 *
	 * <p>{@code create_time} / {@code update_time} 的列定义里写着
	 * {@code ON UPDATE CURRENT_TIMESTAMP}，看着像是"库里会自动维护"。但这条规则
	 * **只在那一列没有被显式赋值时才生效** —— 而 {@code updateById(实体)} 会把
	 * 实体上的每个非 null 字段都写进 SET，其中就包括刚从库里读出来的那两个时间戳。
	 * 于是 MySQL 老老实实把你给它的旧值写了回去，自动更新根本轮不上。
	 *
	 * <p>症状是"编辑完保存，列表上那个时间纹丝不动"—— 用户会以为没保存上。
	 * 而它不会报错、也不会让任何一条现有的用例变红：已有的断言只要求
	 * {@code updateTime >= createTime}，旧值当然满足。
	 *
	 * <p>DATETIME 只到秒，所以要跨过一秒才能真正区分"变了"和"没变"。
	 */
	@Test
	@DisplayName("修改之后 update_time 往后走，create_time 不动")
	void updateAdvancesUpdateTime() throws InterruptedException {
		UserContext.set(USER_ID);

		ExpenseVO created = expenseService.create(dto("10.00", "餐饮", day(7, 1), ""));
		Thread.sleep(1100);

		ExpenseVO updated = expenseService.update(created.id(), dto("20.00", "餐饮", day(7, 1), ""));

		assertThat(updated.updateTime()).isAfter(created.updateTime());
		assertThat(updated.createTime()).isEqualTo(created.createTime());
		// 再查一次，确认不是 VO 上的一次性修饰
		assertThat(expenseService.detail(created.id()).updateTime()).isAfter(created.updateTime());
	}

	/** 同一条记录，POST 拿到什么、GET 就该拿到什么 */
	@Test
	@DisplayName("新增的返回与随后查详情的结果一致")
	void createResponseMatchesDetail() {
		UserContext.set(USER_ID);

		ExpenseVO created = expenseService.create(dto("88.80", "交通", day(2, 3), "打车"));
		ExpenseVO fetched = expenseService.detail(created.id());

		assertThat(created.amount()).isEqualByComparingTo(fetched.amount());
		assertThat(created.createTime()).isEqualTo(fetched.createTime());
		assertThat(created.updateTime()).isEqualTo(fetched.updateTime());
	}

	/**
	 * <b>汇总的金额不能有浮点误差。</b>
	 *
	 * <p>0.1 + 0.2 在二进制浮点下是 0.30000000000000004 —— 这是"金额不许用
	 * FLOAT/DOUBLE"那条规范的全部理由。这条用例把它变成一个会红的断言：
	 * 哪天有人把列改成 {@code DOUBLE}、或把实体字段换成 {@code double}，
	 * 这里立刻失败，而不是等到某张饼图上印出 0.30000000000000004。
	 *
	 * <p>数据是用原生 JDBC 以 {@link BigDecimal} 种进去的，绕开实体 ——
	 * 否则"实体已经是 double 了"这件事会被种数据这一步掩盖。
	 */
	@Test
	@DisplayName("按分类汇总：0.10 + 0.20 恰好是 0.30")
	void summaryDoesNotDrift() {
		seed("0.10", "餐饮", day(3, 1));
		seed("0.20", "餐饮", day(3, 2));

		UserContext.set(USER_ID);

		assertThat(expenseService.summaryByCategory(day(3, 1), day(3, 31)))
				.singleElement()
				.satisfies(row -> assertThat(row.amount().toPlainString()).isEqualTo("0.30"));

		assertThat(expenseService.summaryByMonth(TEST_YEAR).get(2).amount().toPlainString())
				.isEqualTo("0.30");
	}

	/**
	 * 月度汇总恒为 12 项、按月份升序、没记录的月份补 {@code 0.00}。
	 *
	 * <p>年份取一个**没有任何数据**的年份，这样断言的就是纯粹的补零行为。
	 * 用有数据的年份反而测不准：分不清某一项是补出来的还是查出来的。
	 */
	@Test
	@DisplayName("按月汇总恒为 12 项、升序、空月份是 0.00")
	void monthSummaryIsCompleteAndOrdered() {
		UserContext.set(USER_ID);

		List<MonthSummaryVO> summary = expenseService.summaryByMonth(TEST_YEAR);

		assertThat(summary).hasSize(12);
		assertThat(summary).extracting(MonthSummaryVO::month).containsExactly(
				"1991-01", "1991-02", "1991-03", "1991-04", "1991-05", "1991-06",
				"1991-07", "1991-08", "1991-09", "1991-10", "1991-11", "1991-12");
		// 补出来的 0 也要是 "0.00" 而不是 "0"：同一份响应里两种写法是契约上的不一致
		assertThat(summary).allSatisfy(
				m -> assertThat(m.amount().toPlainString()).isEqualTo("0.00"));
	}

	/**
	 * {@code year} 不传时用**业务意义上的**今年，而不是抛异常、也不用 JVM 默认时区。
	 *
	 * <p>断言只检查月份前缀是当前年份 —— 不种数据，所以金额是什么都不影响这条。
	 */
	@Test
	@DisplayName("按月汇总不传年份时用今年")
	void monthSummaryDefaultsToCurrentYear() {
		UserContext.set(USER_ID);

		int currentYear = WorkbenchTime.today().getYear();
		List<MonthSummaryVO> summary = expenseService.summaryByMonth(null);

		assertThat(summary).hasSize(12);
		assertThat(summary.get(0).month()).isEqualTo(currentYear + "-01");
		assertThat(summary.get(11).month()).isEqualTo(currentYear + "-12");
	}

	/**
	 * 空值落成空串而不是 null。
	 *
	 * <p>MyBatis-Plus 默认的字段更新策略是 {@code NOT_NULL}，传 null 的话那条
	 * SET 子句整个消失，旧备注原样留在库里 —— 界面上看着已清空，刷新一下又回来了。
	 */
	@Test
	@DisplayName("备注传 null 时落成空串，且真的写进了库")
	void nullRemarkBecomesEmptyStringAndPersists() {
		UserContext.set(USER_ID);

		ExpenseVO created = expenseService.create(dto("10.00", "餐饮", day(4, 1), "有备注"));
		ExpenseVO updated = expenseService.update(created.id(), dto("10.00", "餐饮", day(4, 1), null));

		assertThat(updated.remark()).isEmpty();
		// 关键的一步：不信返回值，重新查一次库
		assertThat(expenseService.detail(created.id()).remark()).isEmpty();
	}

	/** 分类首尾的空格要 trim 掉，否则库里会同时存在"餐饮"和"餐饮 "两条其实是同一分类的记录 */
	@Test
	@DisplayName("分类首尾空格被去掉")
	void trimsCategory() {
		UserContext.set(USER_ID);

		ExpenseVO created = expenseService.create(dto("10.00", "  餐饮  ", day(4, 2), "  带空格的备注  "));

		assertThat(created.category()).isEqualTo("餐饮");
		assertThat(created.remark()).isEqualTo("带空格的备注");
	}

	/**
	 * 非法分类要被拒，且提示里列出**当前**的合法取值。
	 *
	 * <p>提示从 {@code ExpenseCategory} 现推而不是再抄一遍字面量 ——
	 * 否则将来改了枚举、提示还列着旧值，用户照着改还是被拒。
	 */
	@Test
	@DisplayName("非法分类 → 业务异常，提示里列出合法取值")
	void rejectsUnknownCategory() {
		UserContext.set(USER_ID);

		assertThatThrownBy(() -> expenseService.create(dto("10.00", "吃喝", day(5, 1), "")))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("餐饮")
				.hasMessageContaining("其他")
				.hasMessageContaining("吃喝");
	}

	/** 全空格的分类等同于空，同样要被拒 —— 不能靠"没写"绕过校验 */
	@Test
	@DisplayName("分类只有空格 → 业务异常")
	void rejectsBlankCategory() {
		UserContext.set(USER_ID);

		assertThatThrownBy(() -> expenseService.create(dto("10.00", "   ", day(5, 2), "")))
				.isInstanceOf(BusinessException.class);
	}

	/**
	 * 起止日期颠倒时返回 400 而不是"查不到"。
	 *
	 * <p>反过来的区间在 SQL 里就是恒假，会安静地返回空列表 ——
	 * 而空列表在有筛选条件的页面上看起来就是"这段时间没花钱"。
	 * 一个错误的问题得到一个看似合理的答案，比直接报错难查得多。
	 */
	@Test
	@DisplayName("开始日期晚于结束日期 → 业务异常")
	void rejectsReversedDateRange() {
		UserContext.set(USER_ID);

		assertThatThrownBy(() -> expenseService.page(1, 10, day(6, 30), day(6, 1), null))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("开始日期");

		assertThatThrownBy(() -> expenseService.summaryByCategory(day(6, 30), day(6, 1)))
				.isInstanceOf(BusinessException.class);
	}

	/**
	 * 筛选用的分类**不**做合法性校验，非法值按"查不到"处理。
	 *
	 * <p>与写入时的严格校验不矛盾：写入是要落库的值，筛选只是个查询条件，
	 * "没有匹配"本身就是一个正当答案。这条用例把这个**刻意的差异**钉住，
	 * 免得将来有人"顺手统一一下"，让用户在下拉框里试错时一直看到红字。
	 */
	@Test
	@DisplayName("用非法分类去筛选：空结果，不报错")
	void unknownCategoryFilterIsNotAnError() {
		UserContext.set(USER_ID);

		assertThat(expenseService.page(1, 10, null, null, "不存在的分类").records()).isEmpty();
		assertThat(expenseService.page(1, 10, null, null, "不存在的分类").total()).isZero();
	}

	/** year 超出可用范围时报错，而不是让 {@code LocalDate.of} 抛异常变成 500 */
	@Test
	@DisplayName("year 超出范围 → 业务异常（不是 500）")
	void rejectsOutOfRangeYear() {
		UserContext.set(USER_ID);

		assertThatThrownBy(() -> expenseService.summaryByMonth(100000))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("year");
	}

}
