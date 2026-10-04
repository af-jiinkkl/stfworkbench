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
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

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
		task.setSortOrder(dto.sortOrder() != null ? dto.sortOrder() : nextSortOrder(dto.planDate()));

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
		// completed / completedTime 不在这里改：它们归 updateCompleted 管
		// sortOrder 也不在这里改：排序归 reorder 管（见 PlanTaskUpdateDTO 的类注释）

		planTaskMapper.updateById(task);
		return PlanTaskVO.from(task);
	}

	/**
	 * 按 {@code taskIds} 给出的顺序重排当天的任务。
	 *
	 * <p><b>为什么要求 {@code taskIds} 是当天的完整集合</b>：少传的那几条会保留原值、
	 * 可能会插在中间，于是用户在界面上拖出一个顺序、库里存的是另一个 —— 而且不报错。
	 * 这与 {@code SemesterServiceImpl} 里"改小 {@code totalWeeks} 时有课落到范围外就拒绝"
	 * 是同一种取舍：**宁可让用户刷新一次，也不要静默给出一个错的顺序**。
	 *
	 * <p><b>为什么逐条 update 而不是一条 {@code CASE WHEN}</b>：仓库没有批量更新的先例，
	 * 而一天的条数是个位到两位数，循环写在事务里最直白；{@code CASE WHEN} 得靠
	 * {@code setSql} 拼字符串，反而把注入面打开了。
	 */
	@Override
	@Transactional
	public List<PlanTaskVO> reorder(LocalDate planDate, List<Long> taskIds) {
		if (planDate == null || taskIds == null || taskIds.isEmpty()) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "planDate 与 taskIds 不能为空");
		}

		List<PlanTask> tasks = planTaskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
				.eq(PlanTask::getPlanDate, planDate));

		// 集合一致 + 个数一致：后者顺带把"有重复"挡掉（去重后会比原列表短）
		Set<Long> current = tasks.stream().map(PlanTask::getId).collect(Collectors.toSet());
		Set<Long> submitted = new HashSet<>(taskIds);
		if (submitted.size() != taskIds.size() || !current.equals(submitted)) {
			// 消息刻意笼统：别人的 id、不存在的 id、已删除的 id 走到这里都是同一句话，
			// 所以它不泄露任何一条是否存在（同 requireOwned 那条 404 的理由）
			throw new BusinessException(ResultCode.BAD_REQUEST, "任务列表已变化，请刷新后重试");
		}

		for (int i = 0; i < taskIds.size(); i++) {
			planTaskMapper.update(null, new LambdaUpdateWrapper<PlanTask>()
					.eq(PlanTask::getId, taskIds.get(i))
					.set(PlanTask::getSortOrder, i));
		}

		// 回读再返回：仓库规矩是写接口必须回读一次，避免返回一份与库里对不上的数据。
		// 这里就地按新顺序拼 VO 也能得到一样的结果，但回读能顺带验掉"到底写进去了没有"
		return listByDate(planDate);
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
	 * 当天已有任务的最大排序值 + 1，用来把新建的任务放到末尾（当天没有任务则是 0）。
	 *
	 * <p>早先这里是写死的 {@code 0}，靠"同值再按 id 升序"才等效于"追加到末尾" ——
	 * 那要求当天所有任务的 {@code sortOrder} 全是 0。手工拖动排序一旦把它改成
	 * 0/1/2…，这个前提就没了：新任务带着 0 进来，会与原先那条 0 并列，
	 * 只因为 id 更大而排到**第 2 位**，不是末尾。
	 *
	 * <p>取最大值在 Java 里做、不在 SQL 里做：条件里不拼任何字面量，
	 * 用户条件仍由拦截器注入，与 {@code listByDate} 是同一个 wrapper 形状。
	 */
	private int nextSortOrder(LocalDate planDate) {
		return planTaskMapper.selectList(new LambdaQueryWrapper<PlanTask>()
						.eq(PlanTask::getPlanDate, planDate))
				.stream()
				.mapToInt(task -> task.getSortOrder() == null ? 0 : task.getSortOrder())
				.max()
				.orElse(-1) + 1;
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
