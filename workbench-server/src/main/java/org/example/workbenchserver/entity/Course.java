package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 课程实体，对应 {@code wb_course}（docs/数据模型.md §4.8）。
 *
 * <p><b>本类刻意没有 {@code userId} 字段，这不是漏写</b>，理由同
 * {@link PlanTask}：租户插件只在 INSERT 时补列清单里没有的列，
 * 实体一旦带 {@code userId} 就成了绕过隔离的越权写入通道。
 *
 * <p><b>{@code semesterId} 只是个外键值，没有任何约束保证它指向的学期
 * 属于同一个人。</b>租户插件管的是本表自己的 {@code user_id}，管不到这一列。
 * 所以"新增 / 修改时先确认那个学期是自己的"必须在 Service 里显式做
 * （见 {@code CourseServiceImpl}）—— 漏了不会报错，只会安静地留下一条
 * 指向别人学期的悬空记录，而当事人两边都看不到它。
 *
 * <p><b>一门课占几个格子就存几条记录</b>，不设"多时段"字段。
 * 代价是同一门课的若干条要一起改；好处是课表上每一格都是独立、
 * 可单独拖动或删除的记录 —— 而课表恰恰是"天天在微调"的东西。
 *
 * <p>{@code dayOfWeek} / {@code weekType} 用 Integer 而不是枚举：
 * 列是 TINYINT，用枚举要额外配 {@code IEnum} 或 {@code @EnumValue} 才能落库，
 * 而合法取值范围由 Service 校验即可（1-7、0/1/2）。
 */
@TableName("wb_course")
public class Course {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 所属学期。**必须校验归属**，见类注释 */
	private Long semesterId;

	/** 课程名 */
	private String name;

	/** 任课老师。可空串，但不可为 null */
	private String teacher;

	/** 上课地点。可空串，但不可为 null */
	private String location;

	/** 星期几 1-7 */
	private Integer dayOfWeek;

	/** 开始节次 1-6 */
	private Integer startSection;

	/** 结束节次 1-6，不小于 startSection */
	private Integer endSection;

	/** 起始周 */
	private Integer startWeek;

	/** 结束周，不早于 startWeek，且不超过所属学期的总周数 */
	private Integer endWeek;

	/** 周类型：0 每周 / 1 单周 / 2 双周。单双周是相对**学期第几周**而言的 */
	private Integer weekType;

	// 这两个时间戳**归数据库管**，Java 一侧永远不许写它们，理由同 Expense 的同名字段
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

	public Long getSemesterId() {
		return semesterId;
	}

	public void setSemesterId(Long semesterId) {
		this.semesterId = semesterId;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}

	public String getTeacher() {
		return teacher;
	}

	public void setTeacher(String teacher) {
		this.teacher = teacher;
	}

	public String getLocation() {
		return location;
	}

	public void setLocation(String location) {
		this.location = location;
	}

	public Integer getDayOfWeek() {
		return dayOfWeek;
	}

	public void setDayOfWeek(Integer dayOfWeek) {
		this.dayOfWeek = dayOfWeek;
	}

	public Integer getStartSection() {
		return startSection;
	}

	public void setStartSection(Integer startSection) {
		this.startSection = startSection;
	}

	public Integer getEndSection() {
		return endSection;
	}

	public void setEndSection(Integer endSection) {
		this.endSection = endSection;
	}

	public Integer getStartWeek() {
		return startWeek;
	}

	public void setStartWeek(Integer startWeek) {
		this.startWeek = startWeek;
	}

	public Integer getEndWeek() {
		return endWeek;
	}

	public void setEndWeek(Integer endWeek) {
		this.endWeek = endWeek;
	}

	public Integer getWeekType() {
		return weekType;
	}

	public void setWeekType(Integer weekType) {
		this.weekType = weekType;
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
