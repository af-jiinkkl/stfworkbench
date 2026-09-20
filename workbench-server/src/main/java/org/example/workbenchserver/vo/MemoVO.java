package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Memo;

import java.time.LocalDateTime;

/**
 * 备忘列表项。
 *
 * <p><b>不带 {@code content}</b>（docs/接口清单.md §6）：正文可能很长，
 * 列表页用不上，整页带过去纯属浪费带宽。要看正文走 {@code GET /api/memo/{id}}。
 *
 * <p>{@code summary} 是正文的一小段截断，不是完整正文。留它有两个用处：
 * 标题为空时列表才不至于是一片空白；按正文搜到的条目，用户能看出它为什么被搜出来。
 *
 * @param id         主键
 * @param title      标题，可能为空串
 * @param summary    正文摘要，无正文时为空串
 * @param updateTime 最后修改时间，列表按它倒序
 */
public record MemoVO(Long id, String title, String summary, LocalDateTime updateTime) {

	/** 摘要长度。一行放得下、又不至于把列表撑成一堆重复的前缀 */
	private static final int SUMMARY_MAX = 60;

	public static MemoVO from(Memo memo) {
		return new MemoVO(
				memo.getId(),
				memo.getTitle() != null ? memo.getTitle() : "",
				summarize(memo.getContent()),
				memo.getUpdateTime());
	}

	/**
	 * 把正文压成一行短的。
	 *
	 * <p>截断按**码点**而不是字符下标：{@code substring(0, 60)} 可能正好从中间
	 * 劈开一个代理对（emoji 就是两个 char），劈出来的半个字符在 JSON 里是乱码。
	 */
	private static String summarize(String content) {
		if (content == null) {
			return "";
		}

		// 换行、连续空白压成单个空格：列表里是一行，留着 \n 会把行高撑乱
		String flat = content.replaceAll("\\s+", " ").trim();

		if (flat.codePointCount(0, flat.length()) <= SUMMARY_MAX) {
			return flat;
		}
		return flat.substring(0, flat.offsetByCodePoints(0, SUMMARY_MAX)) + "…";
	}

}
