package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 备忘录实体，对应 {@code wb_memo}（docs/数据模型.md §4.4）。
 *
 * <p><b>本类刻意没有 {@code userId} 字段，这不是漏写</b>，理由同
 * {@link PlanTask}：租户插件只在 INSERT 时补列清单里没有的列，
 * 实体一旦带 {@code userId} 就成了绕过隔离的越权写入通道。
 *
 * <p>{@code content} 可为空 —— 建表脚本里它就是 NULL 列。空正文由
 * Service 归一成空串而非保留 null，理由见 {@code MemoServiceImpl.apply}。
 */
@TableName("wb_memo")
public class Memo {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 标题。没写标题时存空串，列表会退而显示正文摘要 */
	private String title;

	/** 正文。TEXT 列，可长可空 */
	private String content;

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

	public String getTitle() {
		return title;
	}

	public void setTitle(String title) {
		this.title = title;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
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
