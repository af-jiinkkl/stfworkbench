package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 消费记录实体，对应 {@code wb_expense}（docs/数据模型.md §4.6）。
 *
 * <p><b>本类刻意没有 {@code userId} 字段，这不是漏写</b>，理由同
 * {@link PlanTask}：租户插件只在 INSERT 时补列清单里没有的列，
 * 实体一旦带 {@code userId} 就成了绕过隔离的越权写入通道。
 *
 * <p><b>{@code amount} 是 {@link BigDecimal}，不许换成 {@code double}。</b>
 * 用浮点存钱是经典事故：{@code 0.1 + 0.2 != 0.3}，汇总几十条之后
 * 分位上出现的偏差会直接印在图表上。这里从实体到 VO 到 JSON 全程 BigDecimal，
 * 中间不经过任何一次二进制浮点转换 —— 转换一旦发生，精度就找不回来了。
 *
 * <p>{@code category} 用 String 而不是 {@code ExpenseCategory} 枚举：
 * MyBatis-Plus 对枚举要额外配 {@code IEnum} 或 {@code @EnumValue} 才能落库，
 * 而这一列本来就是 {@code VARCHAR(20)}、将来要放开自定义分类。
 * 校验放在 Service 里做（见 {@code ExpenseServiceImpl#normalizeCategory}），
 * 合法取值见 {@link org.example.workbenchserver.common.ExpenseCategory}。
 */
@TableName("wb_expense")
public class Expense {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 金额（元）。DECIMAL(10,2)，上限 99,999,999.99 */
	private BigDecimal amount;

	/** 消费分类，取值为预置清单之一 */
	private String category;

	/** 消费日期。用 DATE 而不是 DATETIME —— 记账只关心哪一天 */
	private LocalDate expenseDate;

	/** 备注。可空串，但不可为 null：null 会被 MyBatis-Plus 的 NOT_NULL 更新策略跳过 */
	private String remark;

	// 这两个时间戳**归数据库管**，Java 一侧永远不许写它们。
	//
	// updateStrategy = NEVER 让它们不出现在任何 UPDATE 的 SET 子句里 ——
	// 这不是省事，是必需的。列定义上的 ON UPDATE CURRENT_TIMESTAMP
	// **只在那一列没被显式赋值时才生效**，而 updateById(实体) 默认会把
	// 实体上每个非 null 字段都写进 SET：更新前刚 selectById 读出来的那两个
	// 时间戳正好是旧值，于是 MySQL 老老实实把旧值写了回去，自动更新轮不上。
	//
	// 症状是"编辑完保存，列表上那个时间纹丝不动"，用户以为没保存上。
	// 详见 ExpenseServiceTest#updateAdvancesUpdateTime —— 那条用例就是为此写的。
	@TableField(updateStrategy = FieldStrategy.NEVER)
	private LocalDateTime createTime;

	@TableField(updateStrategy = FieldStrategy.NEVER)
	private LocalDateTime updateTime;

	/** 逻辑删除：0 未删除，非 0 为删除时间戳 */
	@TableLogic
	private Long deleted;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public BigDecimal getAmount() {
		return amount;
	}

	public void setAmount(BigDecimal amount) {
		this.amount = amount;
	}

	public String getCategory() {
		return category;
	}

	public void setCategory(String category) {
		this.category = category;
	}

	public LocalDate getExpenseDate() {
		return expenseDate;
	}

	public void setExpenseDate(LocalDate expenseDate) {
		this.expenseDate = expenseDate;
	}

	public String getRemark() {
		return remark;
	}

	public void setRemark(String remark) {
		this.remark = remark;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

	public LocalDateTime getUpdateTime() {
		return updateTime;
	}

	public void setUpdateTime(LocalDateTime updateTime) {
		this.updateTime = updateTime;
	}

	public Long getDeleted() {
		return deleted;
	}

	public void setDeleted(Long deleted) {
		this.deleted = deleted;
	}

}
