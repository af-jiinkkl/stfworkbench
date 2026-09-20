package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 生日与纪念日实体，对应 {@code wb_anniversary}（docs/数据模型.md §4.3）。
 *
 * <p>生日和纪念日**共用这一张表**，靠 {@code type} 区分 —— 两者字段几乎一致，
 * 拆两张表纯属重复。
 *
 * <p><b>本类刻意没有 {@code userId} 字段，这不是漏写</b>，理由同
 * {@link PlanTask}：租户插件只在 INSERT 时补列清单里没有的列，
 * 实体一旦带 {@code userId} 就成了绕过隔离的越权写入通道。
 *
 * <p>只存 {@code month} + {@code day}、不存年份：生日每年重复，年份没有意义。
 * 代价是跨年推算得在 Java 里做，见 {@code YearlyRecurrence}。
 * 将来若要显示年龄或"第几年"，需要补 {@code year} 字段（docs/数据模型.md §4.3 已注明）。
 *
 * <p>{@code createTime} / {@code updateTime} 交给数据库的
 * {@code DEFAULT CURRENT_TIMESTAMP} 与 {@code ON UPDATE CURRENT_TIMESTAMP}。
 */
@TableName("wb_anniversary")
public class Anniversary {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 生日填姓名，纪念日填名称 */
	private String name;

	/** 1 生日 / 2 纪念日 */
	private Integer type;

	/** 关系：自己 / 家人 / 朋友。仅生日有意义，纪念日留空 */
	private String relation;

	/** 月 1-12 */
	private Integer month;

	/** 日 1-31。2 月允许存 29（闰年出生），平年由 YearlyRecurrence 退到 2/28 */
	private Integer day;

	/** 提前提醒天数 1-7 */
	private Integer remindDays;

	private String remark;

	private LocalDateTime createTime;

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

	public Integer getType() {
		return type;
	}

	public void setType(Integer type) {
		this.type = type;
	}

	public String getRelation() {
		return relation;
	}

	public void setRelation(String relation) {
		this.relation = relation;
	}

	public Integer getMonth() {
		return month;
	}

	public void setMonth(Integer month) {
		this.month = month;
	}

	public Integer getDay() {
		return day;
	}

	public void setDay(Integer day) {
		this.day = day;
	}

	public Integer getRemindDays() {
		return remindDays;
	}

	public void setRemindDays(Integer remindDays) {
		this.remindDays = remindDays;
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
