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
