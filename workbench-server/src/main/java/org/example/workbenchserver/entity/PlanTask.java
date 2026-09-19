package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 每日计划任务实体，对应 {@code wb_plan_task}（docs/数据模型.md §4.2）。
 *
 * <p>对外一律走 {@code PlanTaskVO}，本类不直接返回给前端（CLAUDE.md 明确要求）。
 *
 * <p><b>本类刻意没有 {@code userId} 字段，这不是漏写。</b>
 * 数据隔离靠 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段补
 * {@code user_id}，但它在 INSERT 时只补**列清单里没有**的列。若实体带
 * {@code userId} 属性，MyBatis-Plus 会把它（非 null 时）写进列清单，
 * 拦截器见状就不再补 —— 于是"谁把 userId 赋错了值就写进谁的名下"，
 * 变成一条绕过隔离的越权写入路径。
 *
 * <p>没有这个字段，业务代码在类型上就**无法**指定归属，只能由拦截器填。
 * 想读 user_id（如排查问题）直接查库即可。
 *
 * <p>{@code createTime} / {@code updateTime} 不在 Java 里赋值，交给数据库的
 * {@code DEFAULT CURRENT_TIMESTAMP} 与 {@code ON UPDATE CURRENT_TIMESTAMP}，
 * 理由同 {@link User}。
 */
@TableName("wb_plan_task")
public class PlanTask {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 归属日期。任务属于哪一天，与创建时间无关（可以补录昨天的计划） */
	private LocalDate planDate;

	private String content;

	/** 0 未完成 / 1 已完成。用 Integer 而非 Boolean，是为了与接口清单里 {@code "completed": 1} 的字面量一致 */
	private Integer completed;

	/** 完成时间，由服务端维护：勾选时写当前时间，取消勾选置 null */
	private LocalDateTime completedTime;

	private Integer sortOrder;

	private LocalDateTime createTime;

	private LocalDateTime updateTime;

	/** 逻辑删除：0 未删除，非 0 为删除时间戳。取值规则见 application.yml 的 logic-delete-value */
	@TableLogic
	private Long deleted;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public LocalDate getPlanDate() {
		return planDate;
	}

	public void setPlanDate(LocalDate planDate) {
		this.planDate = planDate;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

	public Integer getCompleted() {
		return completed;
	}

	public void setCompleted(Integer completed) {
		this.completed = completed;
	}

	public LocalDateTime getCompletedTime() {
		return completedTime;
	}

	public void setCompletedTime(LocalDateTime completedTime) {
		this.completedTime = completedTime;
	}

	public Integer getSortOrder() {
		return sortOrder;
	}

	public void setSortOrder(Integer sortOrder) {
		this.sortOrder = sortOrder;
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
