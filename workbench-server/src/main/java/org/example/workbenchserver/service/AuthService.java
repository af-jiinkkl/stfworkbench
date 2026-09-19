package org.example.workbenchserver.service;

import org.example.workbenchserver.dto.LoginDTO;
import org.example.workbenchserver.dto.RegisterDTO;
import org.example.workbenchserver.vo.LoginVO;
import org.example.workbenchserver.vo.UserVO;

/**
 * 认证业务。接口与实现分开放（CLAUDE.md 的分层约定），
 * 便于将来换实现或在测试里替换。
 */
public interface AuthService {

	/**
	 * 注册。
	 *
	 * @throws org.example.workbenchserver.common.exception.BusinessException
	 *         注册开关关闭时抛 403；用户名已存在时抛 500
	 */
	void register(RegisterDTO dto);

	LoginVO login(LoginDTO dto);

	/** 当前登录用户信息 */
	UserVO getCurrentUser();

}
