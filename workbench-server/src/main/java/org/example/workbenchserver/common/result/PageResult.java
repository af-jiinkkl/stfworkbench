package org.example.workbenchserver.common.result;

import java.util.List;

/**
 * 分页返回体，结构见 docs/接口清单.md §1.3：
 *
 * <pre>
 * { "total": 100, "pageNum": 1, "pageSize": 10, "records": [] }
 * </pre>
 *
 * <p>没有直接把 MyBatis-Plus 的 {@code Page} 返回前端。那上面带着一堆
 * 前端用不到、却会随版本变化的东西（{@code orders} / {@code optimizeCountSql} /
 * {@code searchCount}），既泄露实现细节，字段名也和某个持久层库绑死了 ——
 * 哪天换掉 MyBatis-Plus，接口契约就跟着变。
 *
 * <p>{@code total} 用 {@code long}：MySQL 的 COUNT 本来就是 BIGINT，
 * 用 int 接将来数据量大了会溢出。
 *
 * @param <T> 记录类型，通常是 VO，绝不是 Entity
 */
public record PageResult<T>(long total, long pageNum, long pageSize, List<T> records) {

	public static <T> PageResult<T> of(long total, long pageNum, long pageSize, List<T> records) {
		return new PageResult<>(total, pageNum, pageSize, records);
	}

}
