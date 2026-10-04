package org.example.workbenchserver.service;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 新闻来源。目前唯一的实现是聚合数据（{@code JuheNewsSourceClient}）。
 *
 * <p>抽成接口只有一个理由：**让测试不碰网络**。抓取这件事的难点全在"第三方
 * 给的响应不按约定来"—— {@code data} 为 null、{@code error_code} 非 0、
 * 时间字符串解析不了 —— 这些都要能反复构造着测。真去调一次聚合数据的话，
 * 用例既慢又不确定，配额还白白消耗掉（需求说明 §3.5 专门强调过配额）。
 *
 * <p>只有一个方法，所以测试可以直接写 lambda：
 * <pre>{@code NewsSourceClient stub = () -> List.of(new NewsSourceClient.FetchedNews(...));}</pre>
 */
public interface NewsSourceClient {

	/**
	 * 拉一次头条新闻。
	 *
	 * @return 解析好的列表；第三方没有数据时返回**空列表**而不是 null
	 * @throws org.example.workbenchserver.common.exception.BusinessException
	 *         第三方返回业务错误（appkey 失效、超配额等）或响应无法解析时
	 */
	List<FetchedNews> fetchHeadlines();

	/**
	 * 从第三方响应里解析出来的一条，还没落库。
	 *
	 * <p>刻意不复用 {@code News} 实体：实体上还有 {@code id}、{@code fetchDate}、
	 * {@code createTime} 三个由本系统定的字段，让来源层去填它们，
	 * 就等于把"哪一天抓的"这个决定权交给了第三方。
	 *
	 * @param publishTime 解析不了时允许为 null，那一条仍然保留
	 */
	record FetchedNews(String title, String source, String url, LocalDateTime publishTime) {

	}

}
