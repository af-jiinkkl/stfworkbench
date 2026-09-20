package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.example.workbenchserver.common.ExpenseCategory;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.dto.ExpenseDTO;
import org.example.workbenchserver.entity.Expense;
import org.example.workbenchserver.mapper.ExpenseMapper;
import org.example.workbenchserver.service.ExpenseService;
import org.example.workbenchserver.vo.CategorySummaryVO;
import org.example.workbenchserver.vo.ExpenseVO;
import org.example.workbenchserver.vo.MonthSummaryVO;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 消费记录业务实现。
 *
 * <p><b>注意全类没有任何一处 {@code user_id} 条件</b>，这是刻意的：
 * 隔离由 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段注入
 * （两个汇总查询也不例外，它们只是多了 {@code GROUP BY} 而已）。
 * 手写反而会掩盖拦截器失效的问题。
 *
 * <p><b>金额全程 {@link BigDecimal}</b>：入参是、实体是、出参是、
 * 中途也没有任何一次 {@code doubleValue()}。两个汇总的数字由数据库的
 * {@code SUM} 算出，前端不再自己加一遍 —— 让浮点参与求和的次数越少越好，
 * 而"少加一遍"最彻底的做法是根本不加。
 */
@Service
public class ExpenseServiceImpl implements ExpenseService {

	/** 不传 pageSize 时的每页条数，与接口清单 §1.3 的默认值一致 */
	private static final int DEFAULT_PAGE_SIZE = 10;

	/** 每页上限。与 {@code PaginationInnerInterceptor} 的 maxLimit 保持一致 */
	private static final int MAX_PAGE_SIZE = 100;

	/**
	 * 补零用的 0 元。
	 *
	 * <p>刻意写成字符串构造而不是 {@code BigDecimal.ZERO}：后者的 scale 是 **0**，
	 * 序列化出去是 {@code 0} 而不是 {@code 0.00}，同一份响应里就会出现
	 * "有记录的月份是 1500.00、没记录的是 0" 这种两种写法的金额。
	 * 前端虽然会转成 number 不受影响，但这是对外契约上的不一致，
	 * 值一次改动、省一次解释。
	 */
	private static final BigDecimal ZERO_AMOUNT = new BigDecimal("0.00");

	/** 月度汇总的月份数量，恒为 12 */
	private static final int MONTHS_PER_YEAR = 12;

	/** year 参数的可用范围。取 LocalDate 能表示的年份，超出去 LocalDate.of 会抛异常变成 500 */
	private static final int MIN_YEAR = 1900;
	private static final int MAX_YEAR = 9999;

	private final ExpenseMapper expenseMapper;

	public ExpenseServiceImpl(ExpenseMapper expenseMapper) {
		this.expenseMapper = expenseMapper;
	}

	@Override
	public PageResult<ExpenseVO> page(int pageNum, int pageSize,
			LocalDate startDate, LocalDate endDate, String category) {

		// 分页参数从 URL 来，取值范围完全由客户端决定，必须先夹到合法范围。
		// 不依赖 MyBatis-Plus 对越界值的容错：它对 size < 0 的处理是"不再改写 SQL"
		// （等于不翻页、整表一次捞出来），而不是"取 0 条" —— 方向恰好是危险的那一边。
		// 详见 MemoServiceImpl#page 的同名注释
		int safePageNum = Math.max(pageNum, 1);
		int safePageSize = pageSize <= 0
				? DEFAULT_PAGE_SIZE
				: Math.min(pageSize, MAX_PAGE_SIZE);

		requireOrderedRange(startDate, endDate);

		LambdaQueryWrapper<Expense> wrapper = new LambdaQueryWrapper<>();

		// 两个日期都是**闭区间**：endDate 是"看到这一天为止"，
		// 写成 < endDate 就会把当天的记录漏掉，而用户选"到今天"时正是想看今天
		if (startDate != null) {
			wrapper.ge(Expense::getExpenseDate, startDate);
		}
		if (endDate != null) {
			wrapper.le(Expense::getExpenseDate, endDate);
		}

		// 分类筛选**不校验合法性**，非法分类按"查不到"处理。
		// 这与写入时严格校验（见 normalizeCategory）不矛盾：写入是要落库的值，
		// 存进去一个不存在的分类，它就会永远躺在库里、在汇总里单独占一行；
		// 而筛选只是个查询条件，"没有匹配"本身就是一个正当答案，
		// 为它报 400 反而会让前端在筛选框里试错时一直看到红字
		String safeCategory = trimToEmpty(category);
		if (!safeCategory.isEmpty()) {
			wrapper.eq(Expense::getCategory, safeCategory);
		}

		// 新的在前。expense_date 只到天，同一天的多条要再按 id 兜一层，
		// 否则翻页时同一条可能一会儿在第二页一会儿在第三页
		wrapper.orderByDesc(Expense::getExpenseDate).orderByDesc(Expense::getId);

		Page<Expense> result = expenseMapper.selectPage(new Page<>(safePageNum, safePageSize), wrapper);

		return PageResult.of(
				result.getTotal(),
				result.getCurrent(),
				result.getSize(),
				result.getRecords().stream().map(ExpenseVO::from).toList());
	}

	@Override
	public ExpenseVO detail(Long id) {
		return ExpenseVO.from(requireOwned(id));
	}

	@Override
	public ExpenseVO create(ExpenseDTO dto) {
		Expense expense = new Expense();
		// 刻意不设 user_id —— 实体上根本没有这个属性，见 Expense 的类注释
		apply(expense, dto);

		expenseMapper.insert(expense);

		// 回读一次再返回，两个理由：
		//
		// 1. create_time / update_time 是数据库的 DEFAULT CURRENT_TIMESTAMP 填的，
		//    MyBatis-Plus 插完不会把生成的值带回实体（同 MemoServiceImpl#create）。
		// 2. **amount 的小数位也是数据库定的**。列是 DECIMAL(10,2)，传 10.5 进去
		//    存的是 10.50，而实体里那份仍是 scale=1 的 10.5。直接返回的话，
		//    POST 的响应写着 10.5、紧接着 GET 却是 10.50 —— 同一份数据两种写法。
		//
		// 这一条比时间戳那条更隐蔽：时间戳是 null 对 有值，一眼看得出来；
		// 而 10.5 和 10.50 数值相等，只有对着 JSON 原文才发现不一致
		return ExpenseVO.from(requireOwned(expense.getId()));
	}

	@Override
	public ExpenseVO update(Long id, ExpenseDTO dto) {
		Expense expense = requireOwned(id);
		apply(expense, dto);

		expenseMapper.updateById(expense);
		// 同理：update_time 由 MySQL 的 ON UPDATE 维护，实体里那份还是上一次的
		return ExpenseVO.from(requireOwned(id));
	}

	@Override
	public void delete(Long id) {
		// 先确认这条是本人的且存在，为的是能准确返回 404。
		// deleteById 本身也会被拦截器补上 user_id 条件，删不到别人的数据 ——
		// 这一步是为了区分"删了"和"本来就没有"，不是为了安全
		requireOwned(id);
		expenseMapper.deleteById(id);
	}

	@Override
	public List<CategorySummaryVO> summaryByCategory(LocalDate startDate, LocalDate endDate) {
		requireOrderedRange(startDate, endDate);

		QueryWrapper<Expense> wrapper = new QueryWrapper<>();
		// 这里用的是字符串列名而不是 Lambda：Lambda 只能引用实体属性，
		// 表达不了 SUM(amount) 这样的表达式
		wrapper.select("category", "SUM(amount) AS amount");

		if (startDate != null) {
			wrapper.ge("expense_date", startDate);
		}
		if (endDate != null) {
			wrapper.le("expense_date", endDate);
		}
		wrapper.groupBy("category");

		// selectMaps 返回的就是 GROUP BY 之后的行。这条 SQL 的 WHERE 同样
		// 一个字都没写 user_id —— 隔离与逻辑删除都靠拦截器，用例见 ExpenseIsolationTest
		List<Map<String, Object>> rows = expenseMapper.selectMaps(wrapper);

		List<CategorySummaryVO> result = new ArrayList<>(rows.size());
		for (Map<String, Object> row : rows) {
			result.add(new CategorySummaryVO(
					asString(row.get("category")),
					asAmount(row.get("amount"))));
		}

		// 金额从大到小。**排序放在 Java 里而不是 SQL 的 ORDER BY**：
		// 按别名（SUM 出来的 amount）排序在各数据库上的支持度不一样，
		// 而这个结果最多六行，排一次的代价可以忽略
		result.sort(Comparator.comparing(CategorySummaryVO::amount).reversed());
		return result;
	}

	@Override
	public BigDecimal sumOf(LocalDate startDate, LocalDate endDate) {
		// 复用分组结果，不再写一条 SUM(amount) 的查询 —— 省的不是这一次查询
		// （分组本身就是一次查询），而是"哪些行该被算进来"只有一处判断。
		// 单独写一条汇总 SQL 的话，将来只给其中一侧加了条件（比如排除某个分类），
		// 饼图的合计和这里的合计就会不一样，而差几毛钱没人会当成 bug 报上来
		return summaryByCategory(startDate, endDate).stream()
				.map(CategorySummaryVO::amount)
				.reduce(ZERO_AMOUNT, BigDecimal::add);
	}

	@Override
	public List<MonthSummaryVO> summaryByMonth(Integer year) {
		int targetYear = resolveYear(year);

		QueryWrapper<Expense> wrapper = new QueryWrapper<>();
		// 用 MONTH(expense_date) 而不是 DATE_FORMAT(expense_date, '%Y-%m')：
		// 年份已经由入参定死了，SQL 里没必要再拼一次前缀再去截字符串。
		// 顺带的好处是这段 wrapper 里不含任何字面量（引号、%），
		// 而 MyBatis-Plus 对传进 wrapper 的字符串是会做注入检查的
		wrapper.select("MONTH(expense_date) AS m", "SUM(amount) AS amount");
		wrapper.ge("expense_date", LocalDate.of(targetYear, 1, 1));
		wrapper.le("expense_date", LocalDate.of(targetYear, MONTHS_PER_YEAR, 31));
		wrapper.groupBy("MONTH(expense_date)");

		List<Map<String, Object>> rows = expenseMapper.selectMaps(wrapper);

		Map<Integer, BigDecimal> byMonth = new HashMap<>();
		for (Map<String, Object> row : rows) {
			byMonth.put(asInt(row.get("m")), asAmount(row.get("amount")));
		}

		// 补零成完整的 12 个月。理由见 MonthSummaryVO 的类注释：
		// GROUP BY 只返回有记录的月份，直接丢给折线图会让 1 月和 5 月
		// 在横轴上挨在一起，看着像"连续两个月都有花销"
		List<MonthSummaryVO> result = new ArrayList<>(MONTHS_PER_YEAR);
		for (int month = 1; month <= MONTHS_PER_YEAR; month++) {
			BigDecimal amount = byMonth.get(month);
			result.add(new MonthSummaryVO(
					"%d-%02d".formatted(targetYear, month),
					amount != null ? amount : ZERO_AMOUNT));
		}
		return result;
	}

	/**
	 * 把入参归一化后写进实体。
	 *
	 * <p>{@code remark} 空值落成**空串而不是 null**，不能省：
	 * MyBatis-Plus 默认的字段更新策略是 {@code NOT_NULL}，{@code updateById}
	 * 会把 null 字段整条跳过。PUT 的语义是全量替换，若用户清空备注时传 null，
	 * 那条 SET 子句会消失，旧备注原封不动留在库里 —— 界面上看着已清空，
	 * 刷新一下又回来了。与备忘录是同一个坑
	 */
	private void apply(Expense expense, ExpenseDTO dto) {
		expense.setAmount(dto.amount());
		expense.setCategory(normalizeCategory(dto.category()));
		expense.setExpenseDate(dto.expenseDate());
		expense.setRemark(trimToEmpty(dto.remark()));
	}

	/**
	 * 校验并归一化分类。
	 *
	 * <p>非法值抛 400 而不是 500：这是用户能改的输入，提示里直接把合法取值列出来。
	 *
	 * <p>先 trim 再校验，不做"容忍空格"的宽容匹配 —— 否则库里会同时存在
	 * "餐饮" 和 "餐饮 " 两条其实是同一分类的记录，按分类汇总时会裂成两行，
	 * 而用户在界面上看是同一个分类出现了两次。
	 */
	private String normalizeCategory(String category) {
		String trimmed = trimToEmpty(category);
		if (!ExpenseCategory.isValid(trimmed)) {
			// 把被拒的值原样带回去：撞上这条的多半不是用户手输（前端是下拉框），
			// 而是前端那份清单和后端这份对不上了 —— 看到收到的到底是什么，一眼能定位
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"分类「" + trimmed + "」不在预置范围内，只能是：" + ExpenseCategory.allowedValues());
		}
		return trimmed;
	}

	/**
	 * 日期区间得是个区间。
	 *
	 * <p>起止颠倒时返回 400 而不是"查不到"：反过来的区间在 SQL 里就是恒假，
	 * 会安静地返回空列表 —— 而空列表在有筛选条件的页面上看起来就是
	 * "这段时间没花钱"，一个错误的问题得到一个看似合理的答案，
	 * 比直接报错难查得多。
	 */
	private void requireOrderedRange(LocalDate startDate, LocalDate endDate) {
		if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能晚于结束日期");
		}
	}

	/**
	 * 解析 year 参数：不传时用**业务意义上的**今年。
	 *
	 * <p>用 {@code Integer} 接参数是刻意的 —— 传空值时它静默变成 null，
	 * 这里正好把 null 解释成"没指定，用今年"。这也是 {@code GlobalExceptionHandler}
	 * 里那条注释提醒过的行为，在这里是想要的而不是隐患。
	 */
	private int resolveYear(Integer year) {
		int target = year != null ? year : WorkbenchTime.today().getYear();
		if (target < MIN_YEAR || target > MAX_YEAR) {
			// 不夹取而报错：把 3024 悄悄夹成 9999 会返回一份用户没要过的数据，
			// 而那个错误只有等他发现图表不对劲才会暴露
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"year 必须在 " + MIN_YEAR + " 到 " + MAX_YEAR + " 之间");
		}
		return target;
	}

	/**
	 * 取出属于当前用户的记录，不存在则 404。
	 *
	 * <p>{@code selectById} 会被拦截器补上 {@code user_id} 条件，拿别人的 id
	 * 必然返回 null。
	 *
	 * <p><b>返回 404 而不是 403</b>：403 等于确认"这个 id 确实存在，只是不归你"，
	 * 主键连续自增，据此能摸出全库的记录规模。理由与其余模块相同。
	 */
	private Expense requireOwned(Long id) {
		if (id == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
		}
		Expense expense = expenseMapper.selectById(id);
		if (expense == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "消费记录不存在");
		}
		return expense;
	}

	/**
	 * 从 selectMaps 的一行里取金额。
	 *
	 * <p>强转 {@code BigDecimal} 而不是 {@code (BigDecimal) value} 直接扔：
	 * {@code SUM} 在 DECIMAL 列上返回的就是 BigDecimal，但万一将来列类型变了
	 * （或者换了驱动），这里会从 {@code ClassCastException} 变成一句
	 * 能看懂的错误，而不是一个 500 堆栈。
	 */
	private static BigDecimal asAmount(Object value) {
		if (value instanceof BigDecimal decimal) {
			return decimal;
		}
		if (value == null) {
			return ZERO_AMOUNT;
		}
		// 兜底：按十进制字符串构造，绝不用 doubleValue() —— 那正是要避开的那条路
		return new BigDecimal(value.toString());
	}

	/** 从 selectMaps 的一行里取月份。MySQL 的 MONTH() 返回 INT */
	private static int asInt(Object value) {
		if (value instanceof Number number) {
			return number.intValue();
		}
		return Integer.parseInt(String.valueOf(value));
	}

	private static String asString(Object value) {
		return value != null ? value.toString() : "";
	}

	private static String trimToEmpty(String value) {
		return value != null ? value.trim() : "";
	}

}
