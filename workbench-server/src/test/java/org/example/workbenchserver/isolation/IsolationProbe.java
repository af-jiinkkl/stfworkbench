package org.example.workbenchserver.isolation;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 仅用于测试数据隔离的探针实体，对应测试里临时创建的 {@code wb_isolation_probe} 表。
 *
 * <p>为什么需要这么个东西：数据隔离拦截器只对**带 user_id 的表**生效，
 * 而现有业务表里 {@code wb_user} 本身没有 user_id、{@code wb_news} 又排除了，
 * 所以拦截器实际一次都没被触发过。安全机制不验证等于没有 ——
 * 这个探针就是用来把它真正跑起来的。
 */
@TableName("wb_isolation_probe")
public class IsolationProbe {

	@TableId(type = IdType.AUTO)
	private Long id;

	private Long userId;

	private String content;

	public IsolationProbe() {
	}

	public IsolationProbe(String content) {
		this.content = content;
	}

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public Long getUserId() {
		return userId;
	}

	public void setUserId(Long userId) {
		this.userId = userId;
	}

	public String getContent() {
		return content;
	}

	public void setContent(String content) {
		this.content = content;
	}

}
