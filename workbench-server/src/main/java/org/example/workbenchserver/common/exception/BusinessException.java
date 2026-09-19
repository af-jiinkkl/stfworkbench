package org.example.workbenchserver.common.exception;

import org.example.workbenchserver.common.result.ResultCode;

/**
 * 业务异常。Service 层遇到可预期的失败（用户名已存在、密码错误、学期下还有课程等）
 * 直接抛这个，由 {@link GlobalExceptionHandler} 统一转成返回体。
 *
 * <p>不要用它来表达"代码 bug"（空指针、越界那种）—— 那些会被兜底的
 * {@code Exception} 处理器接住并记为 500，区分开才知道该不该报警。
 */
public class BusinessException extends RuntimeException {

	private final int code;

	public BusinessException(String msg) {
		this(ResultCode.BUSINESS_ERROR.getCode(), msg);
	}

	public BusinessException(ResultCode resultCode) {
		this(resultCode.getCode(), resultCode.getMsg());
	}

	public BusinessException(ResultCode resultCode, String msg) {
		this(resultCode.getCode(), msg);
	}

	public BusinessException(int code, String msg) {
		super(msg);
		this.code = code;
	}

	public int getCode() {
		return code;
	}

}
