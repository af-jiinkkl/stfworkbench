package org.example.workbenchserver.service;

import org.example.workbenchserver.dto.SemesterDTO;
import org.example.workbenchserver.vo.SemesterVO;

import java.util.List;

/**
 * 学期业务，接口见 docs/接口清单.md §10。
 *
 * <p>全类不带 {@code userId} 参数：当前用户由 {@code UserContext} 提供，
 * 隔离由租户插件在 SQL 生成阶段完成。方法签名里出现 {@code userId}
 * 就等于给了调用方"传别人的 id"这个可能，而那正是要杜绝的。
 */
public interface SemesterService {

	/**
	 * 学期列表，不分页 —— 一个人的学期总共也就几条。
	 *
	 * <p>按 {@code startDate} 倒序：最近开始的排在最前面，
	 * 前端拿第一条当默认选中的学期。
	 */
	List<SemesterVO> list();

	/**
	 * 新增。
	 *
	 * @throws org.example.workbenchserver.common.exception.BusinessException
	 *         {@code startDate} 不是周一时返回 400
	 */
	SemesterVO create(SemesterDTO dto);

	/** 全量替换。{@code PUT} 的语义见 CLAUDE.md 的 RESTful 约定 */
	SemesterVO update(Long id, SemesterDTO dto);

	/**
	 * 删除。
	 *
	 * <p><b>该学期下还有课程时返回 400，不级联删。</b>见 docs/接口清单.md §12：
	 * 学期是低频操作，多一步"先删课程"的成本很低；而级联删除一旦误操作，
	 * 用户一个学期的课表就不可逆地没了。
	 *
	 * @throws org.example.workbenchserver.common.exception.BusinessException
	 *         有课程关联时返回 400
	 */
	void delete(Long id);

}
