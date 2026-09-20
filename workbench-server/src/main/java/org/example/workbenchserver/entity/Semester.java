package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 学期实体，对应 {@code wb_semester}（docs/数据模型.md §4.7）。
 *
 * <p><b>本类刻意没有 {@code userId} 字段，这不是漏写</b>，理由同
 * {@link PlanTask}：租户插件只在 INSERT 时补列清单里没有的列，
 * 实体一旦带 {@code userId} 就成了绕过隔离的越权写入通道。
 *
 * <p><b>{@code startDate} 是第 1 周的周一</b>，这是整张课表的地基：
 * 第 N 周星期几要换算成真实日期，全靠它加天数。基准偏一天，
 * 整个学期的课表就整体偏一天，而显示出来**仍然是一张看着对的课表** ——
 * 没有任何一步会报错。所以这个约束在写入时就要校验
 * （见 {@code SemesterServiceImpl}），不能只写在文档里。
 */
@TableName("wb_semester")
public class Semester {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 学期名称，如"2026 春季学期" */
	private String name;

	/** 第 1 周的**周一**。约定见类注释，写入时校验 */
	private LocalDate startDate;

	/** 总周数。课程那边的 start_week / end_week 以它为上界 */
	private Integer totalWeeks;

	// 这两个时间戳**归数据库管**，Java 一侧永远不许写它们。
	// updateStrategy = NEVER 让它们不出现在任何 UPDATE 的 SET 子句里 ——
	// 理由同 Expense 的同名字段：ON UPDATE CURRENT_TIMESTAMP 只在那一列
	// 没被显式赋值时才生效，而 updateById(实体) 会把读出来的旧时间戳写回去。
	// 漏了的症状是"改完保存，时间纹丝不动"，不报错，也没有用例会变红
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

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public LocalDate getStartDate() {
		return startDate;
	}

	public void setStartDate(LocalDate startDate) {
		this.startDate = startDate;
	}

	public Integer getTotalWeeks() {
		return totalWeeks;
	}

	public void setTotalWeeks(Integer totalWeeks) {
		this.totalWeeks = totalWeeks;
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
