package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.entity.News;
import org.example.workbenchserver.mapper.NewsMapper;
import org.example.workbenchserver.service.NewsService;
import org.example.workbenchserver.service.NewsSourceClient;
import org.example.workbenchserver.vo.NewsVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 新闻读取与抓取实现，见 docs/接口清单.md §8。
 *
 * <p><b>本类里没有、也不该有一个 {@code user_id} 条件。</b>新闻是全局共享的缓存，
 * 八个业务模块里只有它不是按人分的 —— 这个"缺条件"是需求本身
 * （需求说明 §3.5：所有用户看同一份），不是漏写。
 * 也正因为如此，{@code DataIsolationTest} 那一整套探针用例套不到它头上；
 * 它需要的是相反方向的用例：**两个用户拿到的必须是同一份**。
 */
@Service
public class NewsServiceImpl implements NewsService {

	private static final Logger log = LoggerFactory.getLogger(NewsServiceImpl.class);

	/**
	 * 三个列的宽度，与 {@code db/schema.sql} 里的一致。
	 *
	 * <p>超长要**截断**而不是丢掉这一条：标题被截掉尾巴仍然点得开，
	 * 而丢掉的新闻在界面上就是彻底没有。何况这是缓存 ——
	 * 为了一条超长的标题让整批抓取失败（MySQL 严格模式下会拒绝整条 INSERT）
	 * 是最不划算的选择。
	 */
	private static final int TITLE_MAX = 255;

	private static final int SOURCE_MAX = 50;

	private static final int URL_MAX = 500;

	private final NewsMapper newsMapper;

	private final NewsSourceClient sourceClient;

	public NewsServiceImpl(NewsMapper newsMapper, NewsSourceClient sourceClient) {
		this.newsMapper = newsMapper;
		this.sourceClient = sourceClient;
	}

	@Override
	public List<NewsVO> listToday() {
		return listByDate(WorkbenchTime.today());
	}

	@Override
	public List<NewsVO> listByDate(LocalDate fetchDate) {
		return newsMapper.selectList(new LambdaQueryWrapper<News>()
						.eq(News::getFetchDate, fetchDate)
						// 没有发布时间的排最后。MySQL 的 DESC 会把 NULL 排在末尾，
						// 正好是想要的顺序；同一时间戳的几条再按 id 兜底，
						// 否则每次查询的先后可能不一样，列表看着会"自己换位置"
						.orderByDesc(News::getPublishTime)
						.orderByDesc(News::getId))
				.stream()
				.map(NewsVO::of)
				.toList();
	}

	/**
	 * 取前几条。在 Java 里截断而不是往 SQL 里塞 {@code LIMIT}：
	 * 第三方一次最多给 30 条，而一张卡片只要 5 条 ——
	 * 为这点差别在 wrapper 上拼一段 {@code last("LIMIT n")}（那是绕过参数绑定的口子）
	 * 不划算，何况这一年轻轻松松能跑完。
	 */
	@Override
	public List<NewsVO> latestToday(int limit) {
		if (limit <= 0) {
			return List.of();
		}
		return listToday().stream().limit(limit).toList();
	}

	/**
	 * {@inheritDoc}
	 *
	 * <p><b>幂等靠"当天去重"，这件事不能省。</b>抓取每小时跑一次，
	 * 而第三方的头条榜在几个小时内基本不变 —— 不去重的话，同一条新闻
	 * 一天会被写 24 遍，列表上全是重复条目，表也白白长大。
	 * 去重键是 {@code (fetch_date, title)}：标题是这里唯一能当键的东西
	 * （第三方给的 {@code uniquekey} 没存进库，数据模型的表结构里没有那一列，
	 * 见 docs/数据模型.md §4.5）。
	 *
	 * <p>跨天不比对：昨天写过的同一条新闻，今天再出现时算今天的一条新记录 ——
	 * {@code fetch_date} 不同，本来就是两行。
	 */
	@Override
	public int refresh() {
		List<NewsSourceClient.FetchedNews> fetched = sourceClient.fetchHeadlines();

		LocalDate today = WorkbenchTime.today();
		Set<String> existing = existingTitlesOn(today);

		int saved = 0;
		for (NewsSourceClient.FetchedNews item : fetched) {
			if (!existing.add(item.title())) {
				// 本条与当天已有的重复（含同一次抓取里自己重复的），跳过
				continue;
			}
			News news = new News();
			news.setTitle(clip(item.title(), TITLE_MAX));
			news.setSource(clip(item.source(), SOURCE_MAX));
			news.setUrl(clip(item.url(), URL_MAX));
			news.setPublishTime(item.publishTime());
			news.setFetchDate(today);
			// insert 之后**不回读**：本接口不返回这条记录，而 id 与 createTime
			// 都由数据库定 —— 回读一次纯粹是白跑一趟
			newsMapper.insert(news);
			saved++;
		}

		log.info("新闻抓取完成：拉取 {} 条，新写入 {} 条", fetched.size(), saved);
		return saved;
	}

	/**
	 * 当天已经写进库的标题。一次查完，不在循环里逐条查。
	 *
	 * <p>用 {@code toCollection(HashSet::new)} 而不是 {@code Collectors.toSet()}：
	 * 返回的集合会被调用方 {@code add}（同一次抓取内部也要判重），
	 * 而 {@code toSet()} 只承诺返回 {@code Set}，不承诺可变。
	 */
	private Set<String> existingTitlesOn(LocalDate fetchDate) {
		return newsMapper.selectList(new LambdaQueryWrapper<News>()
						.eq(News::getFetchDate, fetchDate)
						// 只要标题这一列 —— 这个 Set 的唯一用途就是判重
						.select(News::getTitle))
				.stream()
				.map(News::getTitle)
				.collect(Collectors.toCollection(HashSet::new));
	}

	private static String clip(String value, int max) {
		if (value == null) {
			return "";
		}
		return value.length() <= max ? value : value.substring(0, max);
	}

}
