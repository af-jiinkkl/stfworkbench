package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.MemoDTO;
import org.example.workbenchserver.service.MemoService;
import org.example.workbenchserver.vo.MemoDetailVO;
import org.example.workbenchserver.vo.MemoVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 备忘录接口，见 docs/接口清单.md §6。
 *
 * <p>只做接参数、调 service、包返回体（CLAUDE.md 的分层约定）。
 * 分页参数的越界夹取放在 Service 里，不在这里 —— 那是业务规则，
 * 换一个入口（比如将来的导出）也得守。
 */
@RestController
@RequestMapping("/api/memo")
public class MemoController {

	private final MemoService memoService;

	public MemoController(MemoService memoService) {
		this.memoService = memoService;
	}

	/**
	 * 分页列表，可按关键词搜索。
	 *
	 * <p>三个查询参数都是可选的，全都有默认值，所以不带参数直接 GET 也能用 ——
	 * 前端首次进页面就是这么调的。
	 *
	 * <p>返回的是 {@link PageResult} 而不是 MyBatis-Plus 的 {@code Page}，
	 * 理由见 {@code PageResult} 的类注释。
	 */
	@GetMapping
	public Result<PageResult<MemoVO>> page(
			@RequestParam(defaultValue = "1") int pageNum,
			@RequestParam(defaultValue = "10") int pageSize,
			@RequestParam(required = false) String keyword) {
		return Result.success(memoService.page(pageNum, pageSize, keyword));
	}

	/** 详情。列表不带正文，要看正文走这里 */
	@GetMapping("/{id}")
	public Result<MemoDetailVO> detail(@PathVariable Long id) {
		return Result.success(memoService.detail(id));
	}

	@PostMapping
	public Result<MemoDetailVO> create(@Valid @RequestBody MemoDTO dto) {
		return Result.success(memoService.create(dto));
	}

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	@PutMapping("/{id}")
	public Result<MemoDetailVO> update(@PathVariable Long id, @Valid @RequestBody MemoDTO dto) {
		return Result.success(memoService.update(id, dto));
	}

	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable Long id) {
		memoService.delete(id);
		return Result.success();
	}

}
