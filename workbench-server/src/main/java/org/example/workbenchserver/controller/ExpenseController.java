package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.ExpenseDTO;
import org.example.workbenchserver.service.ExpenseService;
import org.example.workbenchserver.vo.CategorySummaryVO;
import org.example.workbenchserver.vo.ExpenseVO;
import org.example.workbenchserver.vo.MonthSummaryVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 消费记录接口，见 docs/接口清单.md §9。
 *
 * <p>只做接参数、调 service、包返回体（CLAUDE.md 的分层约定）。
 * 分页夹取、日期区间校验、分类合法性都放在 Service 里，不在这里 ——
 * 那些是业务规则，换一个入口（比如将来的导出）也得守。
 *
 * <p>每个 {@code LocalDate} 查询参数都必须带 {@code @DateTimeFormat}：
 * {@code JacksonConfig} 配的是请求体（JSON）里的日期格式，管不到 URL 查询参数，
 * 后者由 Spring MVC 的参数转换负责。漏写的话 {@code startDate=2026-09-01}
 * 会报 400，而且报错信息只说"类型不匹配"，看不出是缺注解。
 */
@RestController
@RequestMapping("/api/expense")
public class ExpenseController {

	private final ExpenseService expenseService;

	public ExpenseController(ExpenseService expenseService) {
		this.expenseService = expenseService;
	}

	/** 分页列表，三个筛选条件都可选。不带参数就是"全部，按日期倒序" */
	@GetMapping
	public Result<PageResult<ExpenseVO>> page(
			@RequestParam(defaultValue = "1") int pageNum,
			@RequestParam(defaultValue = "10") int pageSize,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
			@RequestParam(required = false) String category) {
		return Result.success(expenseService.page(pageNum, pageSize, startDate, endDate, category));
	}

	/**
	 * 按分类汇总，给饼图。
	 *
	 * <p>路径是两层（{@code /summary/category}）而详情是单层（{@code /{id}}），
	 * 所以两者不会抢路由。真要抢的话 Spring 也会优先匹配更具体的那个。
	 */
	@GetMapping("/summary/category")
	public Result<List<CategorySummaryVO>> summaryByCategory(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return Result.success(expenseService.summaryByCategory(startDate, endDate));
	}

	/**
	 * 按月汇总，给趋势图。**恒返回 12 项**，没记录的月份是 0 ——
	 * 理由见 {@code MonthSummaryVO} 的类注释。
	 *
	 * <p>{@code year} 不传时用业务意义上的今年（东八区，见 {@code WorkbenchTime}）。
	 */
	@GetMapping("/summary/month")
	public Result<List<MonthSummaryVO>> summaryByMonth(
			@RequestParam(required = false) Integer year) {
		return Result.success(expenseService.summaryByMonth(year));
	}

	@GetMapping("/{id}")
	public Result<ExpenseVO> detail(@PathVariable Long id) {
		return Result.success(expenseService.detail(id));
	}

	@PostMapping
	public Result<ExpenseVO> create(@Valid @RequestBody ExpenseDTO dto) {
		return Result.success(expenseService.create(dto));
	}

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	@PutMapping("/{id}")
	public Result<ExpenseVO> update(@PathVariable Long id, @Valid @RequestBody ExpenseDTO dto) {
		return Result.success(expenseService.update(id, dto));
	}

	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable Long id) {
		expenseService.delete(id);
		return Result.success();
	}

}
