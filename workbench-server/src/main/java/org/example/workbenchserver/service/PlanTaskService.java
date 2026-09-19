package org.example.workbenchserver.service;

import org.example.workbenchserver.dto.PlanTaskCompletedDTO;
import org.example.workbenchserver.dto.PlanTaskCreateDTO;
import org.example.workbenchserver.dto.PlanTaskUpdateDTO;
import org.example.workbenchserver.vo.PlanTaskVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 每日计划业务接口，对应 docs/接口清单.md §4。
 *
 * <p>所有方法都**不需要也不接受** userId 参数：归属由数据隔离拦截器
 * 从当前登录态自动注入（docs/数据模型.md §2.2）。把 userId 做成入参
 * 等于给调用方留了一个"传别人的 id"的口子。
 */
public interface PlanTaskService {

	/**
	 * 查某天的任务，按 {@code sortOrder}、{@code id} 升序。
	 *
	 * @param date 目标日期；传 {@code null} 表示"今天"（按东八区算）
	 */
	List<PlanTaskVO> listByDate(LocalDate date);

	/**
	 * 查日期区间内的任务，按日期倒序（回顾场景，最近的排前面）。
	 *
	 * <p>区间跨度不得超过 6 个月，超出抛 400 —— 这是需求里"可查看 6 个月内"
	 * 的落地方式，见 docs/接口清单.md §4。
	 */
	List<PlanTaskVO> listByRange(LocalDate startDate, LocalDate endDate);

	PlanTaskVO create(PlanTaskCreateDTO dto);

	/** 改内容 / 日期 / 排序，但不碰完成状态（后者有自己的 PATCH 接口） */
	PlanTaskVO update(Long id, PlanTaskUpdateDTO dto);

	/** 切换完成状态，并同步维护 {@code completedTime} */
	PlanTaskVO updateCompleted(Long id, PlanTaskCompletedDTO dto);

	void delete(Long id);

}
