package org.example.workbenchserver.common.result;

/**
 * 统一返回体，格式见 CLAUDE.md 与 docs/接口清单.md §1.1：
 *
 * <pre>
 * { "code": 200, "msg": "success", "data": {} }
 * </pre>
 *
 * <p>用 record 而不是普通类：出参只由后端构造、前端只读，
 * 不需要 setter 也不该被修改。Jackson 3 原生支持 record。
 *
 * <p>{@code data} 为 null 时字段会**保留**并输出 {@code "data": null}，
 * 而不是被省略 —— 这与接口清单里 {@code completedTime: null} 的写法一致。
 * 注意别在 application.yml 里配 {@code default-property-inclusion: non_null}。
 *
 * @param <T> 业务数据类型
 */
public record Result<T>(int code, String msg, T data) {

	public static <T> Result<T> success() {
		return success(null);
	}

	public static <T> Result<T> success(T data) {
		return new Result<>(ResultCode.SUCCESS.getCode(), ResultCode.SUCCESS.getMsg(), data);
	}

	public static <T> Result<T> error(ResultCode resultCode) {
		return error(resultCode.getCode(), resultCode.getMsg());
	}

	public static <T> Result<T> error(ResultCode resultCode, String msg) {
		return error(resultCode.getCode(), msg);
	}

	public static <T> Result<T> error(int code, String msg) {
		return new Result<>(code, msg, null);
	}

}
