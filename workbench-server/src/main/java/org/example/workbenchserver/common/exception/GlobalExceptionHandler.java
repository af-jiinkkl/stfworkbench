package org.example.workbenchserver.common.exception;

import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import jakarta.validation.ConstraintViolationException;

import java.util.List;

/**
 * 全局异常处理：把所有异常统一收敛成 {@link Result} 的返回体，
 * 保证前端永远只需要解析一种结构。
 *
 * <p><b>HTTP 状态码与 body 里的 code 保持一致</b>（400 就返回 HTTP 400）。
 * 这样浏览器 Network 面板一眼能看出哪类错，axios 的 error 分支也能直接用
 * {@code error.response.status}。仅 body 里塞 code、HTTP 一律 200 的做法
 * 会让所有前端拦截器都得先解析 body 才知道成败，更容易写漏。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	/** 业务异常：可预期，msg 直接给用户看，不打 error 日志避免噪音 */
	@ExceptionHandler(BusinessException.class)
	public ResponseEntity<Result<Void>> handleBusinessException(BusinessException e) {
		log.debug("业务异常: code={}, msg={}", e.getCode(), e.getMessage());
		return ResponseEntity.status(e.getCode()).body(Result.error(e.getCode(), e.getMessage()));
	}

	/**
	 * {@code @Valid} 校验失败。只取第一条错误返回 —— 前端表单一次展示一条提示即可，
	 * 把全部错误拼成一句话反而不好读。
	 */
	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<Result<Void>> handleValidationException(MethodArgumentNotValidException e) {
		List<FieldError> fieldErrors = e.getBindingResult().getFieldErrors();
		String msg = fieldErrors.isEmpty()
				? ResultCode.BAD_REQUEST.getMsg()
				: fieldErrors.get(0).getField() + " " + fieldErrors.get(0).getDefaultMessage();
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Result.error(ResultCode.BAD_REQUEST, msg));
	}

	/** 直接写在 @RequestParam / @PathVariable 上的校验注解失败 */
	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<Result<Void>> handleConstraintViolation(ConstraintViolationException e) {
		String msg = e.getConstraintViolations().stream()
				.findFirst()
				.map(v -> v.getPropertyPath() + " " + v.getMessage())
				.orElse(ResultCode.BAD_REQUEST.getMsg());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Result.error(ResultCode.BAD_REQUEST, msg));
	}

	/** 请求体不是合法 JSON，或类型对不上（比如给 LocalDate 传了乱码） */
	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<Result<Void>> handleNotReadable(HttpMessageNotReadableException e) {
		log.debug("请求体解析失败: {}", e.getMessage());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Result.error(ResultCode.BAD_REQUEST, "请求体格式错误"));
	}

	/**
	 * URL 里的参数类型转不过去，比如 {@code ?pageNum=abc} 或 {@code /api/memo/xyz}。
	 *
	 * <p>不接住的话会落到兜底分支变成 500 + 一条 error 堆栈。可这是**客户端传错了**，
	 * 不是服务端出了故障 —— 报成 500 既误导前端（以为要重试），
	 * 又让真正需要关注的 500 淹没在噪音里。
	 *
	 * <p>注意它能生效的前提是参数声明成基本类型（{@code int}）而不是包装类型：
	 * 声明成 {@code Integer} 时传 {@code abc} 同样是这个异常，
	 * 但传**空值**会静默变成 null 而不报错，于是 {@code int} 的拆箱空指针
	 * 又绕回了 500。所以分页参数用的是 {@code int} + {@code defaultValue}。
	 */
	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<Result<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
		log.debug("参数类型不匹配: {}={}", e.getName(), e.getValue());
		return ResponseEntity.status(HttpStatus.BAD_REQUEST)
				.body(Result.error(ResultCode.BAD_REQUEST, e.getName() + " 格式不正确"));
	}

	/**
	 * 兜底。走到这里说明是代码缺陷而不是用户操作失误，
	 * 所以记 error 日志（带堆栈）但**不把细节返回给前端**，避免泄露内部结构。
	 */
	@ExceptionHandler(Exception.class)
	public ResponseEntity<Result<Void>> handleUnexpectedException(Exception e) {
		log.error("未预期的异常", e);
		return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
				.body(Result.error(ResultCode.BUSINESS_ERROR, "服务器内部错误，请稍后重试"));
	}

}
