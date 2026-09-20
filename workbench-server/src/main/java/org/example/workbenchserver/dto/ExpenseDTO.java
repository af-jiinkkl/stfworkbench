package org.example.workbenchserver.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * 消费记录入参。新增与修改共用一份 —— 后端的 PUT 是全量替换，
 * 字段集合与 POST 完全一致，没有"不给就不改"的字段，不必拆成两个。
 *
 * <p><b>金额用 {@link BigDecimal} 而不是 {@code double}</b>，理由见
 * {@code Expense} 的类注释。这里多一层风险：{@code double} 在**反序列化**时
 * 就已经把精度丢了（JSON 里的 {@code 0.1} 一进 double 就是 0.1000000000000000055…），
 * 等到写库才发现就已经晚了。
 *
 * <p>{@code @Digits(integer = 8, fraction = 2)} 是照着列的
 * {@code DECIMAL(10,2)} 写的，不能省：MySQL 对超范围的值在严格模式下报错
 * （会变成一句没头没脑的 500），对**小数位**却只是**静默四舍五入** ——
 * 传 {@code 0.005} 进来会存成 {@code 0.01}，用户看到的是"我明明记的 0.005"。
 * 有这条注解，它先拿到一个说得清的 400。
 *
 * <p>{@code category} 只做非空和长度校验，**合法性校验不在这里**：
 * 合法取值是 {@link org.example.workbenchserver.common.ExpenseCategory} 里的清单，
 * 写成 {@code @Pattern("餐饮|交通|…")} 就等于把那份清单又抄了一遍，
 * 两处迟早会不一致。所以放在 Service 里查同一个枚举
 * （见 {@code ExpenseServiceImpl#normalizeCategory}），效果同样是 400。
 */
public record ExpenseDTO(

		@NotNull(message = "不能为空")
		@DecimalMin(value = "0.01", message = "必须大于 0")
		@Digits(integer = 8, fraction = 2, message = "最多 8 位整数、2 位小数")
		BigDecimal amount,

		@NotBlank(message = "不能为空")
		@Size(max = 20, message = "长度不能超过 20 个字")
		String category,

		@NotNull(message = "不能为空")
		LocalDate expenseDate,

		@Size(max = 255, message = "长度不能超过 255 个字")
		String remark

) {
}
