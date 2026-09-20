package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.SemesterDTO;
import org.example.workbenchserver.service.SemesterService;
import org.example.workbenchserver.vo.SemesterVO;
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
 * 学期接口，见 docs/接口清单.md §10。
 *
 * <p>只做接参数、调 service、包返回体（CLAUDE.md 的分层约定）。
 * "开始日期必须是周一"和"有课程时不许删学期"都是业务规则，放在 Service 里 ——
 * 换一个入口（将来的导入）也得守。
 *
 * <p>没有 {@code GET /api/semester/{id}}：学期列表不分页、一次全拿，
 * 前端选中某个学期时手上已经有整份列表了，再给一个详情接口
 * 只是多一条没人调的路由。
 */
@RestController
@RequestMapping("/api/semester")
public class SemesterController {

	private final SemesterService semesterService;

	public SemesterController(SemesterService semesterService) {
		this.semesterService = semesterService;
	}

	/** 学期列表，按开始日期倒序。不分页 */
	@GetMapping
	public Result<List<SemesterVO>> list() {
		return Result.success(semesterService.list());
	}

	@PostMapping
	public Result<SemesterVO> create(@Valid @RequestBody SemesterDTO dto) {
		return Result.success(semesterService.create(dto));
	}

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	@PutMapping("/{id}")
	public Result<SemesterVO> update(@PathVariable Long id, @Valid @RequestBody SemesterDTO dto) {
		return Result.success(semesterService.update(id, dto));
	}

	/** 该学期下还有课程时返回 400，不级联删（见接口清单 §12） */
	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable Long id) {
		semesterService.delete(id);
		return Result.success();
	}

}
