package org.example.workbenchserver.dto;

import jakarta.validation.constraints.Size;

/**
 * 备忘录入参。新增与修改共用一份 —— 后端的 PUT 是全量替换，
 * 字段集合与 POST 完全一致，没有"不给就不改"的字段，不必拆成两个。
 *
 * <p><b>标题和正文都允许为空，但不能同时为空</b>（那条规则在 Service 里）。
 * 这里不用 {@code @NotBlank} 卡标题，是因为随手记一笔往往就是一段话，
 * 强制先起个标题会让人干脆不记了；列表没标题时会退而显示正文摘要。
 *
 * <p>正文上限 15000 字，不是随手取的：{@code TEXT} 的上限是 **65535 字节**，
 * 而 utf8mb4 下一个字符最多占 4 字节，所以约 16000 字符就会顶到列的上限。
 * 不设这个上限的话，超长粘贴进来会撞成 MySQL 的 1406 错误、
 * 经兜底处理器变成一句没头没脑的"服务器内部错误"；有了它是一条能看懂的 400。
 */
public record MemoDTO(

		@Size(max = 100, message = "长度不能超过 100 个字")
		String title,

		@Size(max = 15000, message = "长度不能超过 15000 个字")
		String content

) {
}
