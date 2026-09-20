package org.example.workbenchserver.service;

import org.example.workbenchserver.common.result.PageResult;
import org.example.workbenchserver.dto.MemoDTO;
import org.example.workbenchserver.vo.MemoDetailVO;
import org.example.workbenchserver.vo.MemoVO;

/**
 * 备忘录业务接口，对应 docs/接口清单.md §6。
 *
 * <p>唯一一个带分页的模块 —— 每日计划和生日纪念日的量天然很小，
 * 备忘录则会越攒越多，列表必须分页。
 */
public interface MemoService {

	/**
	 * 分页查询。
	 *
	 * @param pageNum  页码，从 1 开始，越界会被夹到合法范围
	 * @param pageSize 每页条数，越界会被夹到 1-100
	 * @param keyword  可选。对标题和正文做模糊匹配，空则不筛
	 */
	PageResult<MemoVO> page(int pageNum, int pageSize, String keyword);

	/** 查详情。不存在或不属于当前用户都是 404 */
	MemoDetailVO detail(Long id);

	/**
	 * 当前用户的备忘总条数（已逻辑删除的不计）。
	 *
	 * <p>给首页聚合用，只需要一个数字，不必把记录查出来再 {@code size()} ——
	 * 备忘录会越攒越多，为了显示一个数字把几百条正文全捞进内存没有道理。
	 */
	long count();

	MemoDetailVO create(MemoDTO dto);

	/** 全量替换。没传的字段按"清空"处理，不是"保持原样" */
	MemoDetailVO update(Long id, MemoDTO dto);

	void delete(Long id);

}
