package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 新闻缓存实体，对应 {@code wb_news}（docs/数据模型.md §4.5）。
 *
 * <p><b>本类不带 {@code userId}，而且这是全项目唯一一张"本该如此"的表。</b>
 * 其余七张表的实体不带 {@code userId} 是**不许带** —— 租户插件只在 INSERT 时
 * 补列清单里没有的列，实体一旦带上它就成了绕过隔离的写入通道（见 {@link PlanTask}）。
 * 而这一张压根没有那一列：新闻是所有用户共享的一份缓存，不是谁的私有数据。
 * 拦截器靠 {@code MybatisPlusConfig#TABLES_WITHOUT_USER_ID} 跳过它。
 *
 * <p>同理没有 {@code updateTime} 与 {@code deleted}：缓存不做逻辑删除，
 * 也不存在"改一条新闻"这个操作 —— 定时任务只插不更，过期与否由
 * {@code fetch_date} 体现（接口只取当天的，旧的自然就看不见了）。
 * 数据模型里写明这是已确认的例外，不是漏写。
 */
@TableName("wb_news")
public class News {

	@TableId(type = IdType.AUTO)
	private Long id;

	/** 标题。第三方的 title 偶尔带首尾空白，由 Service 统一 trim */
	private String title;

	/** 来源。存第三方给的 author_name，缺失时是空串 */
	private String source;

	/** 原文链接。第三方给的是手机端页面地址 */
	private String url;

	/**
	 * 发布时间，**可为 null**。
	 *
	 * <p>第三方偶尔给出解析不了的时间字符串。那种记录仍然值得留下
	 * （标题和链接都在，点开就能看），只是排序时排在最后。
	 * 为了这一条把它整条丢掉，代价比收益大。
	 */
	private LocalDateTime publishTime;

	/**
	 * 抓取日期 —— 接口按"今天抓的"取数，用的就是这一列。
	 *
	 * <p>它和 {@code publishTime} 是两件事，不能合并：凌晨抓到一条昨晚发布的新闻，
	 * 它属于今天的列表，但发布时间还在昨天。合并成 publishTime 的话，
	 * 要么它从今天的列表里消失，要么昨晚的新闻混进昨天那批。
	 */
	private LocalDate fetchDate;

	/**
	 * 入库时间，由数据库填。
	 *
	 * <p>本表没有任何 UPDATE 路径，这个注解实际上永远不会生效 ——
	 * 留着是因为 {@code EntityTimestampConventionTest} 扫的是"凡声明了
	 * {@code createTime} / {@code updateTime} 的实体都必须带"，而不是
	 * "每个实体都必须声明这两个字段"。它守的是"下一个新实体别忘了加注解"
	 * 那件事，而扫描器没法知道哪张表将来会多出一条更新路径。
	 */
	@TableField(updateStrategy = FieldStrategy.NEVER)
	private LocalDateTime createTime;

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

	public String getSource() {
		return source;
	}

	public void setSource(String source) {
		this.source = source;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public LocalDateTime getPublishTime() {
		return publishTime;
	}

	public void setPublishTime(LocalDateTime publishTime) {
		this.publishTime = publishTime;
	}

	public LocalDate getFetchDate() {
		return fetchDate;
	}

	public void setFetchDate(LocalDate fetchDate) {
		this.fetchDate = fetchDate;
	}

	public LocalDateTime getCreateTime() {
		return createTime;
	}

	public void setCreateTime(LocalDateTime createTime) {
		this.createTime = createTime;
	}

}
