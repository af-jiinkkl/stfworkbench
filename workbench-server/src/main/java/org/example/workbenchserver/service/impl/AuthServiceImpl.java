package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.config.WorkbenchProperties;
import org.example.workbenchserver.dto.LoginDTO;
import org.example.workbenchserver.dto.RegisterDTO;
import org.example.workbenchserver.entity.User;
import org.example.workbenchserver.mapper.UserMapper;
import org.example.workbenchserver.security.JwtUtil;
import org.example.workbenchserver.security.UserContext;
import org.example.workbenchserver.service.AuthService;
import org.example.workbenchserver.vo.LoginVO;
import org.example.workbenchserver.vo.UserVO;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

/**
 * 认证业务实现。
 */
@Service
public class AuthServiceImpl implements AuthService {

	private final UserMapper userMapper;

	private final PasswordEncoder passwordEncoder;

	private final JwtUtil jwtUtil;

	private final WorkbenchProperties properties;

	public AuthServiceImpl(UserMapper userMapper, PasswordEncoder passwordEncoder, JwtUtil jwtUtil,
			WorkbenchProperties properties) {
		this.userMapper = userMapper;
		this.passwordEncoder = passwordEncoder;
		this.jwtUtil = jwtUtil;
		this.properties = properties;
	}

	@Override
	public void register(RegisterDTO dto) {
		if (!properties.getRegistration().isEnabled()) {
			throw new BusinessException(ResultCode.FORBIDDEN, "注册功能已关闭");
		}

		User user = new User();
		user.setUsername(dto.username());
		// BCrypt 自动生成随机盐，绝不要自己拼盐或用 MD5（CLAUDE.md 明确禁止）
		user.setPassword(passwordEncoder.encode(dto.password()));
		// nickname 为空时默认取 username（docs/接口清单.md §3）
		user.setNickname(StringUtils.hasText(dto.nickname()) ? dto.nickname() : dto.username());
		user.setAvatar("");

		try {
			userMapper.insert(user);
		}
		catch (DuplicateKeyException e) {
			// 唯一键 uk_username(username, deleted) 拦下来了。
			// 不做「先查再插」：那种检查存在竞态 —— 两个请求同时查到"不存在"，
			// 然后都去插入。真正的唯一性保证只能是数据库的唯一索引，
			// 所以直接插、撞了再转成友好提示才是可靠的写法。
			throw new BusinessException("用户名已被占用");
		}
	}

	@Override
	public LoginVO login(LoginDTO dto) {
		User user = userMapper.selectOne(
				new LambdaQueryWrapper<User>().eq(User::getUsername, dto.username()));

		// 「用户不存在」和「密码错误」返回同一句话，不区分。
		// 区分开就等于提供了一个用户名枚举接口，攻击者可以据此筛出有效账号。
		if (user == null || !passwordEncoder.matches(dto.password(), user.getPassword())) {
			throw new BusinessException("用户名或密码错误");
		}

		String token = jwtUtil.generate(user.getId(), user.getUsername());
		return new LoginVO(token, UserVO.from(user));
	}

	@Override
	public UserVO getCurrentUser() {
		User user = userMapper.selectById(UserContext.require());
		// token 合法但用户已被删除/不存在（比如刚注销）。逻辑删除会让这里的
		// selectById 查不到记录，于是返回 401 让前端回到登录页。
		if (user == null) {
			throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
		}
		return UserVO.from(user);
	}

}
