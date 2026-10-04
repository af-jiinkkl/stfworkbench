package org.example.workbenchserver.common.result;

/**
 * 统一返回码，取值来自 CLAUDE.md 的接口约定与 docs/接口清单.md §1.2。
 */
public enum ResultCode {

	/** 成功 */
	SUCCESS(200, "success"),

	/** 参数错误：校验失败，msg 说明具体字段 */
	BAD_REQUEST(400, "参数错误"),

	/** 未登录 / token 失效，前端应跳转登录页 */
	UNAUTHORIZED(401, "未登录或登录已过期"),

	/** 无权限，例如试图访问他人资源、或注册开关已关闭 */
	FORBIDDEN(403, "无权限"),

	/** 资源不存在 */
	NOT_FOUND(404, "资源不存在"),

	/**
	 * 路径存在但请求方法不对，例如对只支持 {@code PUT/DELETE} 的
	 * {@code /api/semester/{id}} 发 {@code GET}。
	 *
	 * <p>它只在前后端对不上时才出现（真实用户点不出这个错），
	 * 所以信息是给开发看的：配着响应头 {@code Allow} 一眼就能看出该改成什么。
	 * 不并进 400 —— 那样就分不清"方法用错了"和"参数传错了"。
	 */
	METHOD_NOT_ALLOWED(405, "该接口不支持这个请求方法"),

	/** 业务异常，msg 为可直接展示给用户的提示 */
	BUSINESS_ERROR(500, "业务异常");

	private final int code;

	private final String msg;

	ResultCode(int code, String msg) {
		this.code = code;
		this.msg = msg;
	}

	public int getCode() {
		return code;
	}

	public String getMsg() {
		return msg;
	}

}
