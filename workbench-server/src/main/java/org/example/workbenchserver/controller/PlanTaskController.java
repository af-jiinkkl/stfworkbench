package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.PlanTaskCompletedDTO;
import org.example.workbenchserver.dto.PlanTaskCreateDTO;
import org.example.workbenchserver.dto.PlanTaskOrderDTO;
import org.example.workbenchserver.dto.PlanTaskUpdateDTO;
import org.example.workbenchserver.service.PlanTaskService;
import org.example.workbenchserver.vo.PlanTaskVO;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
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
 * 每日计划接口，见 docs/接口清单.md §4。
 *
 * <p>只做接参数、调 service、包返回体（CLAUDE.md 的分层约定）。
 * 这里的 {@code @DateTimeFormat} 必须写：{@code JacksonConfig} 配的是
 * 请求体（JSON）里的日期格式，管不到 URL 查询参数 —— 后者由 Spring MVC 的
 * 参数转换负责。不写的话 {@code date=2026-09-18} 会转换失败报 400。
 */
@RestController
@RequestMapping("/api/plan-task")
public class PlanTaskController {

	private final PlanTaskService planTaskService;

	public PlanTaskController(PlanTaskService planTaskService) {
		this.planTaskService = planTaskService;
	}

	/** 查某天的任务。{@code date} 省略时表示今天（由 service 按东八区解析） */
	@GetMapping
	public Result<List<PlanTaskVO>> list(
			@RequestParam(required = false)
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		return Result.success(planTaskService.listByDate(date));
	}

	/** 查日期区间内的任务，跨度不得超过 6 个月 */
	@GetMapping("/range")
	public Result<List<PlanTaskVO>> range(
			@RequestParam
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
			@RequestParam
			@DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
		return Result.success(planTaskService.listByRange(startDate, endDate));
	}

	@PostMapping
	public Result<PlanTaskVO> create(@Valid @RequestBody PlanTaskCreateDTO dto) {
		return Result.success(planTaskService.create(dto));
	}

	@PutMapping("/{id}")
	public Result<PlanTaskVO> update(@PathVariable Long id, @Valid @RequestBody PlanTaskUpdateDTO dto) {
		return Result.success(planTaskService.update(id, dto));
	}

	/**
	 * 重排当天的任务：提交当天的完整 id 列表，按新顺序排列。
	 *
	 * <p>不逐条走 {@code PUT /{id}} —— 那条的 {@code content} 是必填，
	 * 为了改个顺序得把正文原样传回来，而且拖一次要发 N 个请求、没有原子性。
	 *
	 * <p>路径是字面量 {@code /order}，与上面的模板 {@code /{id}} 不冲突：
	 * Spring 匹配路径时字面量优先于模板，所以 {@code /api/plan-task/order}
	 * 不会被当成 {@code id=order}。
	 */
	@PutMapping("/order")
	public Result<List<PlanTaskVO>> reorder(@Valid @RequestBody PlanTaskOrderDTO dto) {
		return Result.success(planTaskService.reorder(dto.planDate(), dto.taskIds()));
	}

	/**
	 * 切换完成状态。
	 *
	 * <p>用 PATCH 而不是 PUT：这里只改一个字段，PUT 的语义是全量替换
	 * （docs/接口清单.md §4 专门解释了这一点）。
	 */
	@PatchMapping("/{id}/completed")
	public Result<PlanTaskVO> updateCompleted(@PathVariable Long id,
			@Valid @RequestBody PlanTaskCompletedDTO dto) {
		return Result.success(planTaskService.updateCompleted(id, dto));
	}

	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable Long id) {
		planTaskService.delete(id);
		return Result.success();
	}

}
