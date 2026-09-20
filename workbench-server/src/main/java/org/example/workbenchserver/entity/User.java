package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 用户表实体，对应 {@code wb_user}。
 *
 * <p><b>本类禁止直接返回给前端</b>（CLAUDE.md 明确要求），
 * 因为它带着 {@code password} 哈希。对外一律走 {@code UserVO}。
 *
 * <p>{@code createTime} / {@code updateTime} 刻意不在 Java 里赋值：
 * 两者的值交给数据库的 {@code DEFAULT CURRENT_TIMESTAMP} 和
 * {@code ON UPDATE CURRENT_TIMESTAMP} 产生。MyBatis-Plus 默认的
 * 字段策略是 NOT_NULL —— 属性为 null 时不会出现在 INSERT/UPDATE 语句里，
 * 因此 DB 默认值能正常生效，也就不需要再写一个 MetaObjectHandler。
 */
@TableName("wb_user")
public class User {

	@TableId(type = IdType.AUTO)
	private Long id;

	private String username;

	private String password;

	private String nickname;

	private String avatar;

	/** QQ 登录标识，当前版本未启用，仅预留 */
	private String qqOpenid;

	// 两个时间戳归数据库管，Java 一侧不许写。完整理由见 Expense 的同名字段。
	// 本表目前只有 INSERT（AuthServiceImpl），加这两行不影响任何现有行为 ——
	// 加是因为它迟早会有"改昵称"这类写路径，而那时漏注解的出错方式
	// 和其余四个实体一模一样：不报错，只是时间不动
	@TableField(updateStrategy = FieldStrategy.NEVER)
	private LocalDateTime createTime;

	@TableField(updateStrategy = FieldStrategy.NEVER)
	private LocalDateTime updateTime;

	/**
	 * 逻辑删除标记：0 未删除，非 0 为删除时间戳。
	 *
	 * <p>具体取值来自 application.yml 的 {@code logic-delete-value: UNIX_TIMESTAMP()}。
	 * 这里显式标注 {@code @TableLogic} 是为了让「这个字段是逻辑删除」这件事在实体上可见，
	 * 而不是只藏在配置文件里。
	 */
	@TableLogic
	private Long deleted;

	public Long getId() {
		return id;
	}

	public void setId(Long id) {
		this.id = id;
	}

	public String getUsername() {
		return username;
	}

	public void setUsername(String username) {
		this.username = username;
	}

	public String getPassword() {
		return password;
	}

	public void setPassword(String password) {
		this.password = password;
	}

	public String getNickname() {
		return nickname;
	}

	public void setNickname(String nickname) {
		this.nickname = nickname;
	}

	public String getAvatar() {
		return avatar;
	}

	public void setAvatar(String avatar) {
		this.avatar = avatar;
	}

	public String getQqOpenid() {
		return qqOpenid;
	}

	public void setQqOpenid(String qqOpenid) {
		this.qqOpenid = qqOpenid;
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
