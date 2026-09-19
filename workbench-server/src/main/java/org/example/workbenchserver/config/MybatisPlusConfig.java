package org.example.workbenchserver.config;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.handler.TenantLineHandler;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.TenantLineInnerInterceptor;
import net.sf.jsqlparser.expression.Expression;
import net.sf.jsqlparser.expression.LongValue;
import org.example.workbenchserver.security.UserContext;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.Locale;
import java.util.Set;

/**
 * MyBatis-Plus 插件配置。这里最要紧的是**数据隔离**。
 *
 * <p>docs/接口清单.md §1.6 的安全铁律：所有带 {@code user_id} 的表，
 * 任何查询都必须限定当前用户，且**不许在 Controller/Service 里手写条件**。
 * 本配置用 MyBatis-Plus 的租户插件（{@code TenantLineInnerInterceptor}）
 * 在 SQL 生成阶段自动补 {@code user_id = ?}，把它变成一件"想漏也漏不掉"的事。
 *
 * <p><b>注意跟"多租户"这个命名的区别</b>：这里借用租户插件实现的是**用户级**数据隔离，
 * 租户列就是 {@code user_id}，不是传统 SaaS 的 tenant_id。
 */
@Configuration
@MapperScan("org.example.workbenchserver.mapper")
public class MybatisPlusConfig {

	/**
	 * 没有 {@code user_id} 列的表，必须排除，否则自动补条件会拼出
	 * 引用不存在的列，SQL 直接报错。
	 *
	 * <ul>
	 *   <li>{@code wb_user}：它自己就是用户表</li>
	 *   <li>{@code wb_news}：全局共享的新闻缓存，所有用户看同一份
	 *       （见 docs/数据模型.md 中该表的说明，它不需要用户维度）</li>
	 * </ul>
	 *
	 * <p>将来新增不带 user_id 的表，**必须加到这里**。
	 */
	private static final Set<String> TABLES_WITHOUT_USER_ID = Set.of("wb_user", "wb_news");

	@Bean
	public MybatisPlusInterceptor mybatisPlusInterceptor() {
		MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();

		// ---- 顺序不能颠倒 ----
		// 隔离条件必须在分页**之前**注入。反过来的话，LIMIT 会先按"全部用户的数据"
		// 计算，再被加上 user_id 条件，分页结果就错了（总数和每页内容都会串）。
		interceptor.addInnerInterceptor(new TenantLineInnerInterceptor(new TenantLineHandler() {

			@Override
			public Expression getTenantId() {
				// 每执行一条 SQL 都实时取当前登录用户，而不是启动时取一次。
				// require() 在未登录时抛 401：能走到这里就说明认证环节出了漏子，
				// 此时拒绝查询，绝不"没取到就不过滤"。
				return new LongValue(UserContext.require());
			}

			@Override
			public String getTenantIdColumn() {
				return "user_id";
			}

			@Override
			public boolean ignoreTable(String tableName) {
				return TABLES_WITHOUT_USER_ID.contains(tableName.toLowerCase(Locale.ROOT));
			}

		}));

		PaginationInnerInterceptor pagination = new PaginationInnerInterceptor(DbType.MYSQL);
		// 兜底上限：CLAUDE.md 的分页参数是前端传的，不设上限等于允许
		// 一次拉全表。100 对当前这些"一天几条记录"的场景绰绰有余。
		pagination.setMaxLimit(100L);
		interceptor.addInnerInterceptor(pagination);

		return interceptor;
	}

}
