package org.example.workbenchserver.service.impl;

import org.example.workbenchserver.config.WorkbenchProperties;
import org.example.workbenchserver.service.NewsService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 定时抓取新闻，见 docs/接口清单.md §8。
 *
 * <p>需求说明 §3.5 把它定为**架构决定**而不是后期优化项：聚合数据是按次计费的，
 * 让每个用户的每次刷新都去打一次第三方，几十个用户就能把当天配额耗光。
 * 所以抓取只发生在这里 —— 一条与用户请求完全无关的路径上。
 *
 * <h2>两条容易踩的</h2>
 *
 * <p><b>一是这里没有登录态。</b>本方法跑在调度线程上，{@code UserContext} 是空的，
 * 而租户插件取当前用户时是会**抛 401** 的（见 {@code MybatisPlusConfig}，
 * 它刻意不做"取不到就不过滤"）。也就是说：这个任务只能碰
 * {@code TABLES_WITHOUT_USER_ID} 里的表。往带 {@code user_id} 的表里写一行，
 * 这里会直接炸，而且报的是"未登录"—— 看到那句很难想到是定时任务。
 * 目前它只写 {@code wb_news}，正是那张被排除的表。
 *
 * <p><b>二是异常必须在这里接住。</b>{@code @Scheduled} 的方法抛出异常时，
 * Spring 只把堆栈打进日志，下一次调度照旧 —— 看起来"任务还在跑"，
 * 但每次都失败。而失败的原因往往就那几种（appkey 过期、超配额、
 * 对方改字段），不接住的话每天几百条堆栈会把日志淹掉，
 * 真正要看的那一条沉在里面。这里按"跳过这一轮"处理。
 */
@Component
public class NewsFetchJob {

	private static final Logger log = LoggerFactory.getLogger(NewsFetchJob.class);

	private final NewsService newsService;

	private final WorkbenchProperties properties;

	public NewsFetchJob(NewsService newsService, WorkbenchProperties properties) {
		this.newsService = newsService;
		this.properties = properties;
	}

	/**
	 * 每小时第 7 分钟抓一次（cron 可配，见 application.yml 的 {@code workbench.news.fetch-cron}）。
	 *
	 * <p>挑了非整点：整点是各类服务的高峰，对方接口在整点附近更容易超时。
	 */
	@Scheduled(cron = "${workbench.news.fetch-cron:0 7 * * * *}")
	public void fetch() {
		if (properties.getNews().getAppkey().isBlank()) {
			// 没有 appkey 就安静跳过，一年也不会刷一条 warn：
			// 这是**预期内**的配置状态（本地开发、还没申请 key 的部署），
			// 不是异常。接口那边照常返回库里已有的数据，可能是空数组
			log.debug("未配置 workbench.news.appkey，跳过本轮新闻抓取");
			return;
		}

		try {
			newsService.refresh();
		} catch (Exception e) {
			// 按"这一轮没抓到"处理，不往外抛：抛出去也只是被调度器记一笔，
			// 而下一轮一小时后照常 —— 与其让堆栈每天堆几百条，不如每次一条
			log.warn("本轮新闻抓取失败，跳过：{}", e.getMessage());
		}
	}

}
