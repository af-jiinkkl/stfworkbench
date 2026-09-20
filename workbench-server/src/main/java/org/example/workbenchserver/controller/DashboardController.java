package org.example.workbenchserver.controller;

import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.service.DashboardService;
import org.example.workbenchserver.vo.DashboardVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 首页聚合接口，见 docs/接口清单.md §7。
 *
 * <p>这不是一个 RESTful 资源端点，而是把首页要用的三份数据打成一次响应 ——
 * 接口清单 §7 专门注明了这一点，与 CLAUDE.md 的资源 CRUD 约定不冲突。
 *
 * <p>没有 {@code /api/dashboard/{id}} 之类的方法：它没有"某一条"的概念，
 * 返回的永远是**当前登录用户**的那一份。换句话说，这个接口的"资源"
 * 就是登录态本身，不需要（也不接受）任何参数。
 */
@RestController
@RequestMapping("/api/dashboard")
public class DashboardController {

	private final DashboardService dashboardService;

	public DashboardController(DashboardService dashboardService) {
		this.dashboardService = dashboardService;
	}

	@GetMapping
	public Result<DashboardVO> overview() {
		return Result.success(dashboardService.overview());
	}

}
