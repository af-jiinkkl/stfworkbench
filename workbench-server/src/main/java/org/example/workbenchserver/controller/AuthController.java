package org.example.workbenchserver.controller;

import jakarta.validation.Valid;
import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.dto.LoginDTO;
import org.example.workbenchserver.dto.RegisterDTO;
import org.example.workbenchserver.service.AuthService;
import org.example.workbenchserver.vo.LoginVO;
import org.example.workbenchserver.vo.UserVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证接口，见 docs/接口清单.md §3。
 *
 * <p>Controller 只做三件事：接参数、调 service、包返回体。
 * 不写业务逻辑，也不碰 Mapper（CLAUDE.md 的分层约定）。
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;

	public AuthController(AuthService authService) {
		this.authService = authService;
	}

	@PostMapping("/register")
	public Result<Void> register(@Valid @RequestBody RegisterDTO dto) {
		authService.register(dto);
		return Result.success();
	}

	@PostMapping("/login")
	public Result<LoginVO> login(@Valid @RequestBody LoginDTO dto) {
		return Result.success(authService.login(dto));
	}

	/** 当前登录用户。前端刷新页面后用它验证 token 是否还有效 */
	@GetMapping("/me")
	public Result<UserVO> me() {
		return Result.success(authService.getCurrentUser());
	}

}
