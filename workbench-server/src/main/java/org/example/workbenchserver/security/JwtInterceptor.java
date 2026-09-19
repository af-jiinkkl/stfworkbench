package org.example.workbenchserver.security;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 认证拦截器：解析 {@code Authorization: Bearer <token>}，
 * 把用户 id 放进 {@link UserContext}，供数据隔离拦截器使用。
 *
 * <p>抛出的 {@link BusinessException} 会被 {@code GlobalExceptionHandler} 转成
 * 401 返回体 —— DispatcherServlet 对 preHandle 抛出的异常同样会走
 * HandlerExceptionResolver 链，所以这里不需要自己往 response 里写 JSON。
 */
@Component
public class JwtInterceptor implements HandlerInterceptor {

	private static final String HEADER_NAME = "Authorization";

	private static final String TOKEN_PREFIX = "Bearer ";

	private final JwtUtil jwtUtil;

	public JwtInterceptor(JwtUtil jwtUtil) {
		this.jwtUtil = jwtUtil;
	}

	@Override
	public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
		String header = request.getHeader(HEADER_NAME);
		if (header == null || !header.startsWith(TOKEN_PREFIX)) {
			throw new BusinessException(ResultCode.UNAUTHORIZED, "请先登录");
		}

		String token = header.substring(TOKEN_PREFIX.length()).trim();
		try {
			UserContext.set(jwtUtil.parseUserId(token));
		}
		catch (JwtException | IllegalArgumentException e) {
			// 签名不符、被篡改、已过期、格式非法 —— 一律当作未登录处理，
			// 不回显具体原因，免得给攻击者提供试错反馈。
			throw new BusinessException(ResultCode.UNAUTHORIZED, "登录已过期，请重新登录");
		}
		return true;
	}

	/**
	 * 请求结束**必须**清理 ThreadLocal。
	 *
	 * <p>Tomcat 的工作线程是复用的。不清的话，下一个请求若恰好落在同一线程，
	 * 就会读到上一个用户的 id —— 那等于把别人的数据当成自己的返回，
	 * 且因为没有任何报错而极难发现。
	 *
	 * <p>本方法只在 preHandle 返回 true 时才被调用，而 UserContext 也只在
	 * preHandle 成功后才被赋值，两者是对齐的。
	 */
	@Override
	public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
			Exception ex) {
		UserContext.clear();
	}

}
