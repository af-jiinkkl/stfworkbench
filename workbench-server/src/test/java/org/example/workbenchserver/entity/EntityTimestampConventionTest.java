package org.example.workbenchserver.entity;

import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 一条**结构约定**的守卫：{@code create_time} / {@code update_time} 归数据库管，
 * Java 一侧不许写。
 *
 * <p>为什么值得单独一个测试类，而不是在各模块的服务测试里各写一遍：
 * 这个坑的表现是"改完保存，列表上的时间纹丝不动"，**不报错、
 * 也不会让任何一条已有的用例变红**（旧的时间戳当然满足
 * {@code updateTime >= createTime}）。它 2026-09-21 被发现在四个实体上
 * 已经存在了很久，而当时全部用例都是绿的。
 *
 * <p>各模块写一遍只能守住各自的模块，守不住"下一个新建的实体忘了加注解"。
 * 这里扫的是整个 {@code entity} 包，所以**新模块的实体一落地就被覆盖**，
 * 不必记得回来补一条用例。
 *
 * <p>不连库、毫秒级。它与 {@code ExpenseServiceTest#updateAdvancesUpdateTime}
 * 分工不同：那条真的去改一次记录、证明这个约定在运行时确实生效，
 * 这条只证明"约定还写在那儿"。
 *
 * <p><b>已验证过它逮得住</b>（2026-09-21）：临时摘掉 {@code Memo.updateTime}
 * 上的注解再跑，本类当场变红，且报文直接点名 {@code ["Memo.updateTime"]}。
 * 一条"永远绿灯"的守卫比没有守卫更糟 —— 它会被当成已经守住了。
 */
class EntityTimestampConventionTest {

	private static final String ENTITY_PACKAGE = "org.example.workbenchserver.entity";

	/**
	 * 由数据库维护的两个列。
	 *
	 * <p>将来 {@code wb_news} 落地时它只有 {@code create_time}、没有
	 * {@code update_time} 也没有逻辑删除（见 docs/数据模型.md §4.5，那张表是
	 * 缓存不是业务数据）—— 所以下面只校验"**凡是声明了的**都得加注解"，
	 * 不要求每个实体都必须声明这两个字段。
	 */
	private static final List<String> DB_MANAGED_TIMESTAMPS = List.of("createTime", "updateTime");

	/**
	 * 正向对照：扫描器得真的扫到东西。
	 *
	 * <p>没有这一条，一个**彻底坏掉的扫描**（包名写错、过滤器配错、注解换包）
	 * 会让下面那条用例在一个空集合上空转，永远绿灯 ——
	 * 看上去在守卫约定，实际什么都没守。这与 {@code MemoIsolationTest}
	 * 里"搜索确实能匹配到"那条是同一个用意。
	 */
	@Test
	@DisplayName("实体扫描器确实扫到了实体")
	void scannerFindsEntities() {
		List<Class<?>> entities = scanEntities();

		assertThat(entities)
				.hasSizeGreaterThanOrEqualTo(5)
				.contains(PlanTask.class, Memo.class, Anniversary.class, Expense.class, User.class);
	}

	/**
	 * 凡声明了 {@code createTime} / {@code updateTime} 的实体，都必须带
	 * {@code @TableField(updateStrategy = NEVER)}。
	 *
	 * <p>这个注解的作用是让这两列**不出现在任何 UPDATE 的 SET 子句里**。
	 * 少了它，{@code updateById(实体)} 会把刚查出来的旧时间戳原样写回去，
	 * 而列定义上的 {@code ON UPDATE CURRENT_TIMESTAMP}
	 * **只在那一列没被显式赋值时才生效** —— 于是自动更新永远轮不上。
	 *
	 * <p>断言写成"扫全部 + 收集不合格的"，一次报出所有漏网之鱼，
	 * 而不是在第一个失败处停下：新加一个模块时往往一次加好几个实体。
	 */
	@Test
	@DisplayName("声明了时间戳的实体都禁止 Java 写这两列")
	void everyTimestampIsOwnedByDatabase() {
		List<String> offenders = new ArrayList<>();

		for (Class<?> entity : scanEntities()) {
			for (String name : DB_MANAGED_TIMESTAMPS) {
				Field field;
				try {
					field = entity.getDeclaredField(name);
				}
				catch (NoSuchFieldException e) {
					// 该实体没有这个字段：合法（见 DB_MANAGED_TIMESTAMPS 的注释）
					continue;
				}

				TableField annotation = field.getAnnotation(TableField.class);
				if (annotation == null || annotation.updateStrategy() != FieldStrategy.NEVER) {
					offenders.add(entity.getSimpleName() + "." + name);
				}
			}
		}

		assertThat(offenders)
				.as("这些字段会被 updateById 写进 SET，导致 ON UPDATE CURRENT_TIMESTAMP 失效；"
						+ "补上 @TableField(updateStrategy = FieldStrategy.NEVER)")
				.isEmpty();
	}

	/** 扫 entity 包下所有带 {@code @TableName} 的类 */
	private static List<Class<?>> scanEntities() {
		ClassPathScanningCandidateComponentProvider scanner =
				new ClassPathScanningCandidateComponentProvider(false);
		scanner.addIncludeFilter(new AnnotationTypeFilter(TableName.class));

		List<Class<?>> classes = new ArrayList<>();
		for (BeanDefinition definition : scanner.findCandidateComponents(ENTITY_PACKAGE)) {
			try {
				classes.add(Class.forName(definition.getBeanClassName()));
			}
			catch (ClassNotFoundException e) {
				throw new IllegalStateException("扫到的类加载不了: " + definition.getBeanClassName(), e);
			}
		}
		return classes;
	}

}
