package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.AnniversaryDTO;
import org.example.workbenchserver.service.AnniversaryService;
import org.example.workbenchserver.vo.AnniversaryVO;
import org.example.workbenchserver.vo.UpcomingAnniversaryVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 生日与纪念日接口，见 docs/接口清单.md §5。
 *
 * <p>只做接参数、调 service、包返回体（CLAUDE.md 的分层约定）。
 *
 * <p>这个模块没有 {@code LocalDate} 查询参数，也就不需要
 * {@code PlanTaskController} 里那套 {@code @DateTimeFormat} ——
 * 日期是算出来的（{@code nextDate}），不是传进来的。
 */
@RestController
@RequestMapping("/api/anniversary")
public class AnniversaryController {

	private final AnniversaryService anniversaryService;

	public AnniversaryController(AnniversaryService anniversaryService) {
		this.anniversaryService = anniversaryService;
	}

	/** 全部记录。接口清单里没有分页 —— 这个列表天然很小，不值得加 */
	@GetMapping
	public Result<List<AnniversaryVO>> list() {
		return Result.success(anniversaryService.list());
	}

	/**
	 * 即将到来的记录，供首页提醒用。
	 *
	 * <p>路径是固定的 {@code /upcoming}，与 {@code /{id}} 不冲突 ——
	 * 本模块没有"查单条"接口（接口清单 §5 只列了这 5 个）。
	 * 将来若要加 {@code GET /{id}}，Spring 会优先匹配字面量 {@code /upcoming}，
	 * 顺序上仍然安全。
	 */
	@GetMapping("/upcoming")
	public Result<List<UpcomingAnniversaryVO>> upcoming() {
		return Result.success(anniversaryService.upcoming());
	}

	@PostMapping
	public Result<AnniversaryVO> create(@Valid @RequestBody AnniversaryDTO dto) {
		return Result.success(anniversaryService.create(dto));
	}

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	@PutMapping("/{id}")
	public Result<AnniversaryVO> update(@PathVariable Long id, @Valid @RequestBody AnniversaryDTO dto) {
		return Result.success(anniversaryService.update(id, dto));
	}

	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable Long id) {
		anniversaryService.delete(id);
		return Result.success();
	}

}
