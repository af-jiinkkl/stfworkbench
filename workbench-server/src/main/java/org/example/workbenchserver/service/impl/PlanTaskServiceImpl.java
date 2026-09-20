package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.dto.PlanTaskCompletedDTO;
import org.example.workbenchserver.dto.PlanTaskCreateDTO;
import org.example.workbenchserver.dto.PlanTaskUpdateDTO;
import org.example.workbenchserver.entity.PlanTask;
import org.example.workbenchserver.mapper.PlanTaskMapper;
import org.example.workbenchserver.service.PlanTaskService;
import org.example.workbenchserver.vo.PlanTaskVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 每日计划业务实现。
 *
 * <p><b>注意全类没有任何一处 {@code user_id} 条件</b>，这是刻意的：
 * 隔离由 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段注入。
 * 在这里手写会给人一种"隔离靠这行代码"的错觉，反而掩盖了拦截器失效的问题 ——
 * 真失效时手写的那些地方照样是对的，看不出异常。
 */
@Service
public class PlanTaskServiceImpl implements PlanTaskService {

	/**
	 * 查询区间上限，见 docs/接口清单.md §4。
	 */
	private static final int MAX_RANGE_MONTHS = 6;

	private final PlanTaskMapper planTaskMapper;

	public PlanTaskServiceImpl(PlanTaskMapper planTaskMapper) {
		this.planTaskMapper = planTaskMapper;
	}

	@Override
	public List<PlanTaskVO> listByDate(LocalDate date) {
		LocalDate target = (date != null) ? date : WorkbenchTime.today();

		List<PlanTask> tasks = planTaskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
				.eq(PlanTask::getPlanDate, target)
				// 同 sortOrder 的按 id 排。id 是自增的，所以"不给排序值"
				// 等价于"按添加先后排"，前端不必自己维护顺序
				.orderByAsc(PlanTask::getSortOrder)
				.orderByAsc(PlanTask::getId));

		return toVOList(tasks);
	}

	@Override
	public List<PlanTaskVO> listByRange(LocalDate startDate, LocalDate endDate) {
		if (startDate == null || endDate == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "startDate 与 endDate 不能为空");
		}
		if (endDate.isBefore(startDate)) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "endDate 不能早于 startDate");
		}
		// "6 个月内" = 不超过 startDate 往后推 6 个月。用 plusMonths 而不是
		// 固定的 180 天：月份长度不一，按天数算会让"整 6 个月"这种边界时灵时不灵
		if (endDate.isAfter(startDate.plusMonths(MAX_RANGE_MONTHS))) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"查询区间不能超过 " + MAX_RANGE_MONTHS + " 个月");
		}

		List<PlanTask> tasks = planTaskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
				.between(PlanTask::getPlanDate, startDate, endDate)
				// 回顾场景：最近的日期排最前
				.orderByDesc(PlanTask::getPlanDate)
				.orderByAsc(PlanTask::getSortOrder)
				.orderByAsc(PlanTask::getId));

		return toVOList(tasks);
	}

	@Override
	public PlanTaskVO create(PlanTaskCreateDTO dto) {
		PlanTask task = new PlanTask();
		// 刻意不设 user_id —— 实体上根本没有这个属性，见 PlanTask 的类注释
		task.setPlanDate(dto.planDate());
		task.setContent(dto.content().trim());
		task.setCompleted(0);
		task.setCompletedTime(null);
		task.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : 0);

		planTaskMapper.insert(task);

		// insert 之后主键已被回填（IdType.AUTO）。VO 里的字段上面全都赋过值了，
		// 所以不必再 selectById 查一遍 —— 只有 createTime/updateTime 是数据库生成的，
		// 而它们不在 VO 里
		return PlanTaskVO.from(task);
	}

	@Override
	public PlanTaskVO update(Long id, PlanTaskUpdateDTO dto) {
		PlanTask task = requireOwned(id);

		task.setContent(dto.content().trim());
		if (dto.planDate() != null) {
			task.setPlanDate(dto.planDate());
		}
		if (dto.sortOrder() != null) {
			task.setSortOrder(dto.sortOrder());
		}
		// completed / completedTime 不在这里改：它们归 updateCompleted 管

		planTaskMapper.updateById(task);
		return PlanTaskVO.from(task);
	}

	@Override
	public PlanTaskVO updateCompleted(Long id, PlanTaskCompletedDTO dto) {
		PlanTask task = requireOwned(id);

		int completed = dto.completed();
		LocalDateTime completedTime = (completed == 1) ? LocalDateTime.now(WorkbenchTime.ZONE) : null;

		// ⚠️ 这里必须走 UpdateWrapper 显式 set，不能写成 updateById(task)。
		// MyBatis-Plus 默认的字段更新策略是 NOT_NULL —— null 字段会被**跳过**而不是
		// 写进 SQL。取消勾选时 completedTime 正好是 null，用 updateById 的话这条
		// SET 子句会整个消失，数据库里旧值留着，于是"取消勾选后完成时间还在"。
		// 这种 bug 在页面上不明显（界面读的是 completed），排查起来很费劲。
		planTaskMapper.update(null, new LambdaUpdateWrapper<PlanTask>()
				.eq(PlanTask::getId, id)
				.set(PlanTask::getCompleted, completed)
				.set(PlanTask::getCompletedTime, completedTime));

		// 已加载的实体同步一遍，免得为了拼 VO 再查一次库
		task.setCompleted(completed);
		task.setCompletedTime(completedTime);
		return PlanTaskVO.from(task);
	}

	@Override
	public void delete(Long id) {
		// 先确认这条是本人的且存在，为的是能准确返回 404。
		// deleteById 本身也会被拦截器加上 user_id 条件，删不到别人的数据 ——
		// 这一步是为了区分"删了"和"本来就没有"，不是为了安全。
		requireOwned(id);
		planTaskMapper.deleteById(id);
	}

	/**
	 * 取出属于当前用户的任务，不存在则 404。
	 *
	 * <p>{@code selectById} 会被拦截器补上 {@code user_id} 条件，
	 * 所以拿别人的 id 来查必然返回 null。
	 *
	 * <p><b>这种情况返回 404 而不是 403</b>：403 等于告诉对方"这个 id 确实存在，
	 * 只是不归你"，主键是连续的整数，据此就能把全库的记录数量和其他人的数据规模
	 * 摸出来。404 什么都不透露。
	 */
	private PlanTask requireOwned(Long id) {
		if (id == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
		}
		PlanTask task = planTaskMapper.selectById(id);
		if (task == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "任务不存在");
		}
		return task;
	}

	private List<PlanTaskVO> toVOList(List<PlanTask> tasks) {
		return tasks.stream().map(PlanTaskVO::from).toList();
	}

}
