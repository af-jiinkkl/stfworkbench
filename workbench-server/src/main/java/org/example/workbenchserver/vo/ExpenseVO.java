package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Expense;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消费记录出参。列表、详情、新增、修改**共用这一个**。
 *
 * <p>没有像备忘录那样拆出 Detail 版：一条消费记录总共就六个字段，
 * 拆两个 VO 只会让"列表少一个字段、详情多一个字段"的差别变成两处要同步维护的东西。
 * 备忘录拆是因为正文可能上万字，列表页一条也用不上。
 *
 * @param id          主键
 * @param amount      金额（元）。**序列化成 JSON 数字时保留两位小数**，
 *                    即 {@code 1200.50} 而不是 {@code 1200.5} —— 它是
 *                    {@link BigDecimal}，Jackson 按 scale 输出，
 *                    而这个 scale 来自 {@code DECIMAL(10,2)} 列本身
 * @param category    消费分类，预置值之一
 * @param expenseDate 消费日期
 * @param remark      备注，可能为空串，不会是 null
 * @param createTime  创建时间
 * @param updateTime  最后修改时间
 */
public record ExpenseVO(Long id, BigDecimal amount, String category, LocalDate expenseDate,
		String remark, LocalDateTime createTime, LocalDateTime updateTime) {

	public static ExpenseVO from(Expense expense) {
		return new ExpenseVO(
				expense.getId(),
				expense.getAmount(),
				expense.getCategory(),
				expense.getExpenseDate(),
				// 建表脚本里这一列 NOT NULL DEFAULT ''，理论上取不出 null。
				// 兜一下是为了 VO 的契约能说"绝不会是 null" ——
				// 前端少写一个 ?? 就少一处会显示成 "null" 的地方
				expense.getRemark() != null ? expense.getRemark() : "",
				expense.getCreateTime(),
				expense.getUpdateTime());
	}

}
