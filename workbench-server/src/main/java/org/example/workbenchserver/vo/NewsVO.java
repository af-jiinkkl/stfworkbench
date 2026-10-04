package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.News;

import java.time.LocalDateTime;

/**
 * 新闻列表项（docs/接口清单.md §8）。
 *
 * <p>不带 {@code fetchDate}：它是一次查询的**条件**，同一份列表里每条都一样，
 * 返回给前端没有信息量。也不带 {@code createTime}：那是"什么时候入的库"，
 * 只在排查抓取任务时有用，不该出现在接口契约里。
 *
 * <p>{@code publishTime} 可为 null（第三方的时间字符串解析不了时），
 * 前端要按"可能没有时间"来渲染 —— 别写 {@code publishTime.slice(...)} 这种。
 */
public record NewsVO(

		Long id,

		String title,

		/** 来源，可能为空串 */
		String source,

		/** 原文链接，可能为空串 —— 第三方偶尔不给 url */
		String url,

		/** 发布时间，可能为 null */
		LocalDateTime publishTime) {

	public static NewsVO of(News news) {
		return new NewsVO(
				news.getId(),
				news.getTitle(),
				news.getSource(),
				news.getUrl(),
				news.getPublishTime());
	}

}
