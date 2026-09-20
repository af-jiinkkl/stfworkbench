package org.example.workbenchserver.service;

import org.example.workbenchserver.vo.NewsVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 新闻读取与抓取，见 docs/接口清单.md §8。
 *
 * <p><b>这个模块和其余七个模块最大的不同：它没有"当前用户"这个概念。</b>
 * 查询里不带 user_id、也不该带 —— 新闻是所有用户共享的一份缓存
 * （需求说明 §3.5 定的：所有用户看同一份）。所以这里不会出现
 * "拿别人的 id 越权"那类问题，反倒是"别不小心给它加上隔离"值得留意。
 */
public interface NewsService {

	/** 首页卡片显示几条。放在这里而不是让调用方传字面量，见 {@link #latestToday(int)} */
	int HOME_LATEST_COUNT = 5;

	/** 今天抓到的新闻，按发布时间倒序；没有数据时返回空列表 */
	List<NewsVO> listToday();

	/**
	 * 指定某天抓到的新闻，按发布时间倒序。
	 *
	 * <p>存在是为了可测：用例种数据用的是一个固定的过去日期，
	 * 这样它断言的就是自己种的那几条，不会被定时任务当天写进来的真实新闻干扰 ——
	 * 而这个库里真的有定时任务在写东西。
	 */
	List<NewsVO> listByDate(LocalDate fetchDate);

	/**
	 * 今天抓到的新闻里取前 {@code limit} 条，供首页卡片用。
	 *
	 * <p>单独一个方法而不是让首页自己 {@code listToday().subList(0, 5)}：
	 * 那个 5 就成了首页的一处硬编码，而且"取几条"这件事的答案会分叉到两处。
	 */
	List<NewsVO> latestToday(int limit);

	/**
	 * 抓一次并写入缓存，返回本次写入的条数。
	 *
	 * <p>由 {@code NewsFetchJob} 定时调用，**不在任何请求路径上**。
	 * 需求说明 §3.5 把"不许在用户请求时实时调第三方"定为架构决定 ——
	 * 那是按次计费的接口，几十个用户刷几次页面配额就没了。
	 *
	 * @return 写入的条数；{@code 0} 表示这次没有新内容或没配置 appkey
	 */
	int refresh();
}
