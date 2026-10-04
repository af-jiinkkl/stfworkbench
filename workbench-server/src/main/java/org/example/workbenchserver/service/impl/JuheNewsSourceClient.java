package org.example.workbenchserver.service.impl;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.config.WorkbenchProperties;
import org.example.workbenchserver.service.NewsSourceClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

/**
 * 聚合数据（juhe.cn）新闻头条的实现，见 docs/接口清单.md §8。
 *
 * <p>接口是 {@code https://v.juhe.cn/toutiao/index?type=top&key=APPKEY}，
 * 响应形如：
 * <pre>
 * { "error_code": 0, "reason": "success",
 *   "result": { "stat": "1", "data": [ { "title": "...", "date": "2026-09-21 08:00:00",
 *                                          "author_name": "鲁网", "url": "https://..." } ] } }
 * </pre>
 *
 * <h2>三处刻意的写法</h2>
 *
 * <p><b>一是按 {@code error_code} 判定成败，不只看 HTTP 状态。</b>
 * 这个接口业务失败时照样回 HTTP 200，只是 body 里 {@code error_code} 非 0
 * （appkey 失效、超配额都走这条路）。只看状态码会把"配额用完了"当成一次成功抓取，
 * 于是当天新闻一直是空的，而日志里一条错都没有。
 *
 * <p><b>二是拿 {@code byte[]} 再自己按 UTF-8 解。</b>新闻是中文，
 * 而对方响应头里的 charset 不一定靠得住；交给框架按响应头解的话，
 * 一旦它声明的是 ISO-8859-1，整批标题就会变成乱码 —— 而且乱码的新闻
 * 看着像"数据源质量差"，不像一个可以修的 bug。
 *
 * <p><b>三是不复用应用那套 Jackson 配置。</b>这里的 {@code JsonMapper} 是就地新建的：
 * 应用那个由 {@code JacksonConfig} 定制过（日期格式 {@code yyyy-MM-dd HH:mm:ss}、
 * 时区东八区），那是**我们的**对外契约。拿它去解第三方的报文，等于让第三方
 * 的字段格式被我们的配置影响，将来改我们自己的日期格式就会悄悄改到这里的行为。
 */
@Component
public class JuheNewsSourceClient implements NewsSourceClient {

	private static final Logger log = LoggerFactory.getLogger(JuheNewsSourceClient.class);

	private static final String ENDPOINT = "https://v.juhe.cn/toutiao/index";

	/** 头条推荐。当前不分类抓取（需求说明 §8 问题 5 的默认取值：20 条、不分类） */
	private static final String TYPE = "top";

	private static final DateTimeFormatter DATE_TIME_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

	/** 第三方偶尔只给到日期，没有时分秒 */
	private static final DateTimeFormatter DATE_FORMATTER =
			DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final WorkbenchProperties properties;

	private final RestClient restClient;

	/**
	 * 就地新建，不走应用那套定制 —— 理由见类注释第三条。
	 *
	 * <p>只读 String 字段，用不上日期反序列化器那些配置。
	 */
	private final JsonMapper jsonMapper = JsonMapper.builder().build();

	public JuheNewsSourceClient(WorkbenchProperties properties) {
		this.properties = properties;

		// 抓取跑在定时任务的线程上，**必须给超时**：没有超时的话，对方一次
		// 不响应就会把这个线程一直挂着，而日志里什么都没有 ——
		// 表现是"新闻从某天起就再也没更新过"，看不出和网络有关
		SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
		factory.setConnectTimeout(Duration.ofSeconds(5));
		factory.setReadTimeout(Duration.ofSeconds(10));

		this.restClient = RestClient.builder().requestFactory(factory).build();
	}

	@Override
	public List<FetchedNews> fetchHeadlines() {
		String appkey = properties.getNews().getAppkey();
		if (appkey == null || appkey.isBlank()) {
			throw new BusinessException("未配置聚合数据 appkey（环境变量 JUHE_NEWS_KEY），无法抓取新闻");
		}

		byte[] raw = restClient.get()
				.uri(ENDPOINT + "?type={type}&key={key}", TYPE, appkey)
				.retrieve()
				.body(byte[].class);

		if (raw == null || raw.length == 0) {
			throw new BusinessException("新闻接口返回了空响应");
		}

		return parse(new String(raw, StandardCharsets.UTF_8));
	}

	/**
	 * 解析响应。抽成包可见的方法是为了让用例能直接喂一段报文进来 ——
	 * 这些分支（{@code data} 为 null、时间解析不了、字段缺失）真去调接口
	 * 是构造不出来的，而它们恰好是最容易写错的地方。
	 */
	List<FetchedNews> parse(String body) {
		JsonNode root;
		try {
			root = jsonMapper.readTree(body);
		} catch (RuntimeException e) {
			// 把响应前 200 字带上：第三方出错时可能回一个 HTML 错误页，
			// 只记一句"解析失败"的话，得再复现一次才知道它到底回了什么
			throw new BusinessException("新闻接口响应无法解析："
					+ body.substring(0, Math.min(body.length(), 200)));
		}

		// 缺字段时 asInt(-1) 得到 -1，同样按失败处理 —— 报文形状变了就是不该当成成功
		int errorCode = root.path("error_code").asInt(-1);
		if (errorCode != 0) {
			String reason = root.path("reason").asString("");
			throw new BusinessException("新闻接口返回错误："
					+ (reason.isBlank() ? "无说明" : reason) + "（error_code=" + errorCode + "）");
		}

		JsonNode data = root.path("result").path("data");
		// 没有数据时它给的是 null。这是**正常情况**（比如那个时段没有头条），
		// 不是错误 —— 调用方那边也一样，抓不到就抓不到，接口照常返回空数组
		if (data.isMissingNode() || data.isNull() || !data.isArray()) {
			log.debug("新闻接口没有返回数据（result.data 为空）");
			return List.of();
		}

		List<FetchedNews> result = new ArrayList<>(data.size());
		for (JsonNode item : data) {
			String title = text(item, "title");
			if (title.isEmpty()) {
				// 没标题的新闻在界面上是一行空白，点也点不动，留着没有意义
				continue;
			}
			result.add(new FetchedNews(
					title,
					text(item, "author_name"),
					text(item, "url"),
					parseTime(text(item, "date"))));
		}
		return result;
	}

	/** 取字符串字段，缺失/null 时给空串，并 trim —— 第三方给的标题常带首尾空白 */
	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return "";
		}
		return value.asString("").trim();
	}

	/**
	 * 时间字符串 → {@code LocalDateTime}，解析不了就返回 {@code null}。
	 *
	 * <p>**不抛异常**：这一条的时间格式怪，不代表整批新闻都不要了。
	 * 标题和链接都在，点开就能看，无非是排序时排在最后。
	 */
	private static LocalDateTime parseTime(String text) {
		if (text.isEmpty()) {
			return null;
		}
		try {
			return LocalDateTime.parse(text, DATE_TIME_FORMATTER);
		} catch (RuntimeException ignored) {
			// 往下试只有日期的写法
		}
		try {
			return LocalDate.parse(text, DATE_FORMATTER).atStartOfDay();
		} catch (RuntimeException e) {
			log.debug("新闻发布时间解析不了，按 null 处理: {}", text);
			return null;
		}
	}

}
