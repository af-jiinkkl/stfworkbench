package org.example.workbenchserver.service.impl;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.config.WorkbenchProperties;
import org.example.workbenchserver.service.NewsSourceClient;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 第三方新闻响应的解析。**纯单元，不连库、不联网**（<1ms 一个用例）。
 *
 * <p>挑出来单独测，是因为抓取这件事的难点**全在"对方不按约定来"**：
 * {@code data} 为 null、{@code error_code} 非 0、时间字符串是"昨天下午"、
 * 标题字段整个没有。这些分支真去调一次聚合数据是构造不出来的 ——
 * 而且配额按次计费，用例跑一遍就消耗一次。
 *
 * <p>所以 {@code parse} 被留成包可见的（见 {@code JuheNewsSourceClient}），
 * 测试直接喂一段报文进去。构造器只装配 {@code RestClient}，不发请求，
 * 因此这里不需要 Spring 容器：{@code WorkbenchProperties} 是个普通 POJO。
 */
class JuheNewsSourceClientTest {

	private final JuheNewsSourceClient client = new JuheNewsSourceClient(new WorkbenchProperties());

	// ---------- 正常路径 ----------

	@Test
	@DisplayName("正常响应：字段逐个对上，标题的首尾空白被 trim 掉")
	void parsesNormalResponse() {
		List<NewsSourceClient.FetchedNews> news = client.parse("""
				{ "error_code": 0, "reason": "success",
				  "result": { "stat": "1", "data": [
				    { "uniquekey": "abc123", "title": "  今日头条标题  ",
				      "date": "2026-09-21 08:30:00", "author_name": "鲁网",
				      "url": "https://example.com/a", "thumbnail_pic_s": "https://img/x.jpg" }
				  ] } }
				""");

		assertThat(news).hasSize(1);
		assertThat(news.get(0).title()).isEqualTo("今日头条标题");
		assertThat(news.get(0).source()).isEqualTo("鲁网");
		assertThat(news.get(0).url()).isEqualTo("https://example.com/a");
		assertThat(news.get(0).publishTime()).isEqualTo(LocalDateTime.of(2026, 9, 21, 8, 30));
	}

	@Test
	@DisplayName("来源 / 链接缺失时是空串，不是 null")
	void missingOptionalFieldsBecomeEmptyStrings() {
		List<NewsSourceClient.FetchedNews> news = client.parse("""
				{"error_code": 0, "result": {"data": [{"title": "只有标题"}]}}
				""");

		assertThat(news).hasSize(1);
		assertThat(news.get(0).source()).isEmpty();
		assertThat(news.get(0).url()).isEmpty();
		assertThat(news.get(0).publishTime()).isNull();
	}

	// ---------- 失败路径 ----------

	/**
	 * 这个接口**业务失败时照样回 HTTP 200**，只是 body 里 {@code error_code} 非 0。
	 *
	 * <p>只看状态码的话，appkey 失效、超配额都会被当成一次成功抓取 ——
	 * 于是当天新闻一直是空的，而日志里一条错都没有，
	 * 排查方向会拐到"是不是定时任务没跑"，而它跑得好好的。
	 */
	@Test
	@DisplayName("error_code 非 0 抛业务异常，并把 reason 和 code 都带上")
	void errorCodeNonZeroThrows() {
		assertThatThrownBy(() -> client.parse("""
				{"error_code": 10012, "reason": "请求次数超过限额", "result": null}
				"""))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("请求次数超过限额")
				.hasMessageContaining("10012");
	}

	/** 对方改字段名、或者回了个 HTML 错误页时，error_code 压根不在，绝不能当成成功 */
	@Test
	@DisplayName("报文里没有 error_code 也算失败，不当成 0")
	void missingErrorCodeIsFailure() {
		assertThatThrownBy(() -> client.parse("""
				{"result": {"data": [{"title": "看起来很正常的一条"}]}}
				"""))
				.isInstanceOf(BusinessException.class);
	}

	/**
	 * 响应根本不是 JSON（对方给的 HTML 错误页、网关的超时页）时，
	 * 异常里要带上报文前缀 —— 否则得再复现一次才知道它到底回了什么。
	 */
	@Test
	@DisplayName("响应不是 JSON 时把报文前缀带进异常")
	void garbageResponseThrows() {
		assertThatThrownBy(() -> client.parse("<html><body>502 Bad Gateway</body></html>"))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("502 Bad Gateway");
	}

	// ---------- 不完整但不算错的分支 ----------

	/**
	 * {@code result.data} 是 null —— **正常情况**（比如那个时段就没有头条），
	 * 不是错误。抛异常的话，一次空响应会让整个抓取任务记一条 warn，
	 * 而它其实什么毛病都没有。
	 */
	@Test
	@DisplayName("result.data 为 null 时返回空列表，不抛异常")
	void nullDataYieldsEmptyList() {
		assertThat(client.parse("""
				{"error_code": 0, "reason": "success", "result": {"stat": "0", "data": null}}
				""")).isEmpty();
	}

	@Test
	@DisplayName("result 整个缺失、或 data 不是数组时也是空列表")
	void malformedDataYieldsEmptyList() {
		assertThat(client.parse("{\"error_code\": 0, \"reason\": \"success\"}")).isEmpty();
		assertThat(client.parse("""
				{"error_code": 0, "result": {"data": {"title": "本该是数组"}}}
				""")).isEmpty();
	}

	/**
	 * 时间格式怪、或者干脆没给，**这一条仍然要留着** ——
	 * 标题和链接都在，点开就能看，无非是排序时排在最后。
	 * 为了一条时间格式把整批新闻丢掉，代价和收益不成比例。
	 */
	@Test
	@DisplayName("时间只有日期 / 解析不了 / 没给，三种都不影响这条新闻被保留")
	void unparseableTimeKeepsTheItem() {
		List<NewsSourceClient.FetchedNews> news = client.parse("""
				{ "error_code": 0, "result": { "data": [
				  {"title": "只有日期", "date": "2026-09-20"},
				  {"title": "时间很怪", "date": "昨天下午"},
				  {"title": "没给时间"}
				] } }
				""");

		assertThat(news).extracting(NewsSourceClient.FetchedNews::title)
				.containsExactly("只有日期", "时间很怪", "没给时间");
		// 只到日期的按当天 00:00 算
		assertThat(news.get(0).publishTime()).isEqualTo(LocalDateTime.of(2026, 9, 20, 0, 0));
		assertThat(news.get(1).publishTime()).isNull();
		assertThat(news.get(2).publishTime()).isNull();
	}

	/**
	 * 没有标题的条目直接丢掉。
	 *
	 * <p>它在界面上就是一行空白 —— 既看不出是哪条新闻，也点不动。
	 * 而且 {@code title} 是这里唯一的去重键（见 {@code NewsServiceImpl#refresh}），
	 * 空标题还会让后续每一条空标题的新闻互相顶掉。
	 */
	@Test
	@DisplayName("没有标题（或只有空白）的条目丢掉，其余的照常解析")
	void itemsWithoutTitleAreDropped() {
		List<NewsSourceClient.FetchedNews> news = client.parse("""
				{ "error_code": 0, "result": { "data": [
				  {"title": "有标题"},
				  {"date": "2026-09-20"},
				  {"title": "   "},
				  {"title": "也有标题"}
				] } }
				""");

		assertThat(news).extracting(NewsSourceClient.FetchedNews::title)
				.containsExactly("有标题", "也有标题");
	}

}
