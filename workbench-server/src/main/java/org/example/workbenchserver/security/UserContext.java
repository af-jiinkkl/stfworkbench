package org.example.workbenchserver.security;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;

/**
 * 保存「当前请求是谁」，供数据隔离拦截器读取 {@code user_id}。
 *
 * <p>用 ThreadLocal 而非方法参数传递：数据隔离必须对所有查询无条件生效，
 * 若靠 Controller/Service 逐层往下传 userId，迟早有人漏传，
 * 而漏一次的后果是**查到别人的数据**。详见 docs/接口清单.md §1.6 安全铁律。
 *
 * <p><b>务必成对使用</b>：{@link #set} 之后必须在 finally 里 {@link #clear}。
 * 容器的工作线程是复用的，不清就会把上一个请求的用户身份留给下一个请求 ——
 * 这是比"忘了过滤"更隐蔽的越权。JwtInterceptor 的 afterCompletion 负责清。
 *
 * <p>用 {@code remove()} 而不是 {@code set(null)}：线程池场景下只有 remove
 * 才会真正释放 ThreadLocalMap 里的条目。
 */
public final class UserContext {

	private static final ThreadLocal<Long> CURRENT_USER_ID = new ThreadLocal<>();

	private UserContext() {
	}

	public static void set(Long userId) {
		CURRENT_USER_ID.set(userId);
	}

	/** 当前用户 id，未登录时为 null */
	public static Long get() {
		return CURRENT_USER_ID.get();
	}

	/**
	 * 当前用户 id，未登录直接抛 401。
	 *
	 * <p>数据隔离拦截器用这个方法：能走到需要拼 {@code user_id} 的查询，
	 * 就说明这个请求本该已经通过认证。取不到用户 = 认证链路漏了，属于异常情况，
	 * 此时宁可拒绝查询也不能放行（放行就等于不隔离）。
	 */
	public static Long require() {
		Long userId = CURRENT_USER_ID.get();
		if (userId == null) {
			throw new BusinessException(ResultCode.UNAUTHORIZED);
		}
		return userId;
	}

	public static void clear() {
		CURRENT_USER_ID.remove();
	}

}
