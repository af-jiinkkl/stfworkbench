package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Memo;

import java.time.LocalDateTime;

/**
 * 备忘详情，带完整正文。列表项用 {@link MemoVO}。
 *
 * <p>新增和修改也返回这个：前端保存完可以直接拿返回值刷新详情，
 * 不必再发一次 GET，也顺带能看到后端归一化之后的真实内容
 * （标题去没去空格、正文存成了空串还是 null）。
 *
 * @param id         主键
 * @param title      标题，可能为空串
 * @param content    正文，可能为空串
 * @param createTime 创建时间
 * @param updateTime 最后修改时间
 */
public record MemoDetailVO(Long id, String title, String content,
		LocalDateTime createTime, LocalDateTime updateTime) {

	public static MemoDetailVO from(Memo memo) {
		return new MemoDetailVO(
				memo.getId(),
				memo.getTitle() != null ? memo.getTitle() : "",
				memo.getContent() != null ? memo.getContent() : "",
				memo.getCreateTime(),
				memo.getUpdateTime());
	}

}
