package org.example.workbenchserver.service;

import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.dto.ExpenseDTO;
import org.example.workbenchserver.vo.CategorySummaryVO;
import org.example.workbenchserver.vo.ExpenseVO;
import org.example.workbenchserver.vo.MonthSummaryVO;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * 消费记录业务，接口见 docs/接口清单.md §9。
 *
 * <p>全类不带 {@code userId} 参数：当前用户由 {@code UserContext} 提供，
 * 隔离由租户插件在 SQL 生成阶段完成。方法签名里出现 {@code userId}
 * 就等于给了调用方"传别人的 id"这个可能，而那正是要杜绝的。
 */
public interface ExpenseService {

	/**
	 * 分页列表，三个筛选条件都可选。
	 *
	 * @param startDate 起始日期（含），null 表示不限
	 * @param endDate   结束日期（含），null 表示不限
	 * @param category  分类，null / 空串表示不限。**非法分类不报错，按查不到处理**
	 *                  —— 这是筛选条件，不是要写进库的值，理由见实现类的注释
	 */
	PageResult<ExpenseVO> page(int pageNum, int pageSize,
			LocalDate startDate, LocalDate endDate, String category);

	ExpenseVO detail(Long id);

	ExpenseVO create(ExpenseDTO dto);

	ExpenseVO update(Long id, ExpenseDTO dto);

	void delete(Long id);

	/** 按分类汇总，给饼图。只含**有记录**的分类，不补零 */
	List<CategorySummaryVO> summaryByCategory(LocalDate startDate, LocalDate endDate);

	/**
	 * 区间合计。首页的「今日消费」用它（传今天到今天）。
	 *
	 * <p>与 {@link #summaryByCategory} 是同一段口径：实现里就是把它分组出来的
	 * 各行加起来，不再单独写一条 {@code SUM(amount)} 的查询。两条路径各写一遍
	 * 汇总 SQL，就等于"哪些行该被算进来"这件事有了两份判断，将来只给其中一侧
	 * 加了条件，两边给出的合计数就会不一样 —— 差几毛钱没有人会当成 bug 报上来。
	 *
	 * @return 没有记录时是 {@code 0.00} 而不是 null，前端可以直接乘加
	 */
	BigDecimal sumOf(LocalDate startDate, LocalDate endDate);

	/**
	 * 按月汇总，给趋势图。
	 *
	 * @param year null 表示当前年份（按 {@code WorkbenchTime} 的口径）
	 * @return 恒为 12 项、按月份升序、无记录的月份金额为 0
	 */
	List<MonthSummaryVO> summaryByMonth(Integer year);

}
