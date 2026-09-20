package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.CourseDTO;
import org.example.workbenchserver.service.CourseService;
import org.example.workbenchserver.vo.CourseVO;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 课程接口，见 docs/接口清单.md §11。
 *
 * <p>只做接参数、调 service、包返回体（CLAUDE.md 的分层约定）。
 * {@code semesterId} 是否属于自己、周次是否落在学期内，都是业务规则，放在 Service ——
 * 换一个入口（将来的导入）也得守。
 *
 * <p><b>没有 {@code GET /api/course/today}</b>：首页的「今日课程」走
 * {@code GET /api/dashboard} 聚合，不再单独发一次请求。多一个接口就等于
 * 多一处"哪些课算今天的"的判断，而首页和课表页给出的答案必须一致
 * （同 CLAUDE.md 里"别再调一次 /api/anniversary/upcoming"那条）。
 */
@RestController
@RequestMapping("/api/course")
public class CourseController {

	private final CourseService courseService;

	public CourseController(CourseService courseService) {
		this.courseService = courseService;
	}

	/**
	 * 某学期的全部课程，按 {@code dayOfWeek → startSection → id} 返回，
	 * 前端可直接按这个顺序铺进网格。不分页 —— 一个学期就几十门课。
	 */
	@GetMapping
	public Result<List<CourseVO>> listBySemester(@RequestParam Long semesterId) {
		return Result.success(courseService.listBySemester(semesterId));
	}

	@PostMapping
	public Result<CourseVO> create(@Valid @RequestBody CourseDTO dto) {
		return Result.success(courseService.create(dto));
	}

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	@PutMapping("/{id}")
	public Result<CourseVO> update(@PathVariable Long id, @Valid @RequestBody CourseDTO dto) {
		return Result.success(courseService.update(id, dto));
	}

	@DeleteMapping("/{id}")
	public Result<Void> delete(@PathVariable Long id) {
		courseService.delete(id);
		return Result.success();
	}

}
