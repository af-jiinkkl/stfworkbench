package org.example.workbenchserver.controller;

import org.example.workbenchserver.common.result.Result;
import org.example.workbenchserver.service.NewsService;
import org.example.workbenchserver.vo.NewsVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 新闻接口，见 docs/接口清单.md §8。
 *
 * <p>只有读、没有写：新闻由定时任务（{@code NewsFetchJob}）抓取，
 * 用户改不了它。**这个接口一行第三方调用都没有** —— 它只查库。
 * 需求说明 §3.5 把这条定为架构决定：那是按次计费的接口，
 * 在请求路径上实时调用的话，几十个用户刷新几次就把配额耗光了。
 *
 * <p>也不需要任何入参：它返回的永远是"今天抓到的那一批"，
 * 没有按用户、按分类、按日期的筛选 —— 那些都还没有这个需求。
 */
@RestController
@RequestMapping("/api/news")
public class NewsController {

	private final NewsService newsService;

	public NewsController(NewsService newsService) {
		this.newsService = newsService;
	}

	/**
	 * 当日新闻列表，按发布时间倒序。
	 *
	 * <p>当天没有数据时返回**空数组**，不是 404、也不是错误：
	 * 抓取任务失败、或者那天确实还没抓，都走这条路。
	 * 首页少一块内容，好过整页打不开（接口清单 §8 的原话）。
	 */
	@GetMapping
	public Result<List<NewsVO>> listToday() {
		return Result.success(newsService.listToday());
	}

}
