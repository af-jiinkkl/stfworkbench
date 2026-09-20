package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.common.util.YearlyRecurrence;
import org.example.workbenchserver.dto.AnniversaryDTO;
import org.example.workbenchserver.entity.Anniversary;
import org.example.workbenchserver.mapper.AnniversaryMapper;
import org.example.workbenchserver.service.AnniversaryService;
import org.example.workbenchserver.vo.AnniversaryVO;
import org.example.workbenchserver.vo.UpcomingAnniversaryVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.Month;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * 生日与纪念日业务实现。
 *
 * <p><b>注意全类没有任何一处 {@code user_id} 条件</b>，这是刻意的：
 * 隔离由 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段注入。
 * 手写反而会掩盖拦截器失效的问题，理由详见 {@code PlanTaskServiceImpl} 的类注释。
 */
@Service
public class AnniversaryServiceImpl implements AnniversaryService {

	/** 未指定提前提醒天数时取这个值，与建表脚本的 DEFAULT 7 一致 */
	private static final int DEFAULT_REMIND_DAYS = 7;

	private final AnniversaryMapper anniversaryMapper;

	public AnniversaryServiceImpl(AnniversaryMapper anniversaryMapper) {
		this.anniversaryMapper = anniversaryMapper;
	}

	@Override
	public List<AnniversaryVO> list() {
		// 按日历顺序排：用户翻这个列表是想找"哪个月有谁的生日"，
		// 按创建时间排会让同一月份的记录散落各处
		List<Anniversary> all = anniversaryMapper.selectList(new LambdaQueryWrapper<Anniversary>()
				.orderByAsc(Anniversary::getMonth)
				.orderByAsc(Anniversary::getDay)
				.orderByAsc(Anniversary::getId));

		return all.stream().map(AnniversaryVO::from).toList();
	}

	@Override
	public List<UpcomingAnniversaryVO> upcoming() {
		LocalDate today = WorkbenchTime.today();

		// 全量取出在 Java 里筛，条件里只有 user_id（由拦截器补）。
		// 为什么不写进 SQL：本表只存了月和日，"下一次是哪天"依赖跨年判断，
		// SQL 里表达不了（docs/数据模型.md §4.3）。
		// 一个人的这类记录通常只有几十条，全量取出的代价可以忽略。
		List<Anniversary> all = anniversaryMapper.selectList(null);

		List<UpcomingAnniversaryVO> result = new ArrayList<>();
		for (Anniversary anniversary : all) {
			long daysUntil = YearlyRecurrence.daysUntil(
					anniversary.getMonth(), anniversary.getDay(), today);

			// 提醒窗口是逐条的：remindDays 每条可设 1-7，不能拿一个统一的阈值去卡
			if (daysUntil > anniversary.getRemindDays()) {
				continue;
			}

			result.add(UpcomingAnniversaryVO.of(anniversary, daysUntil,
					YearlyRecurrence.nextOccurrence(
							anniversary.getMonth(), anniversary.getDay(), today)));
		}

		// 最紧迫的排最前，首页一眼看到的就是最近的那个
		result.sort(Comparator.comparingLong(UpcomingAnniversaryVO::daysUntil));
		return result;
	}

	@Override
	public AnniversaryVO create(AnniversaryDTO dto) {
		validateMonthDay(dto.month(), dto.day());

		Anniversary anniversary = new Anniversary();
		// 刻意不设 user_id —— 实体上根本没有这个属性，见 Anniversary 的类注释
		apply(anniversary, dto);

		anniversaryMapper.insert(anniversary);
		return AnniversaryVO.from(anniversary);
	}

	@Override
	public AnniversaryVO update(Long id, AnniversaryDTO dto) {
		validateMonthDay(dto.month(), dto.day());

		Anniversary anniversary = requireOwned(id);
		apply(anniversary, dto);

		anniversaryMapper.updateById(anniversary);
		return AnniversaryVO.from(anniversary);
	}

	@Override
	public void delete(Long id) {
		// 先确认这条是本人的且存在，为的是能准确返回 404。
		// deleteById 本身也会被拦截器补上 user_id 条件，删不到别人的数据 ——
		// 这一步是为了区分"删了"和"本来就没有"，不是为了安全。
		requireOwned(id);
		anniversaryMapper.deleteById(id);
	}

	/**
	 * 把入参写进实体。
	 *
	 * <p><b>可空字段一律归一成非 null</b>（{@code relation} / {@code remark} → 空串，
	 * {@code remindDays} → 7），这一步不能省。
	 *
	 * <p>因为 MyBatis-Plus 默认的字段更新策略是 {@code NOT_NULL}：{@code updateById}
	 * 会把 null 字段**整条跳过**。{@code PUT} 的语义是全量替换，若前端清空备注时传的是
	 * null，那条 SET 子句会消失，旧备注原封不动留在库里 —— 界面上看着已清空，
	 * 刷新一下又回来了。这与每日计划"取消勾选后完成时间还在"是同一类坑。
	 * 归一成空串后字段非 null，才会真正写进 SQL。
	 */
	private void apply(Anniversary anniversary, AnniversaryDTO dto) {
		anniversary.setName(dto.name().trim());
		anniversary.setType(dto.type());
		anniversary.setRelation(dto.relation() != null ? dto.relation().trim() : "");
		anniversary.setMonth(dto.month());
		anniversary.setDay(dto.day());
		anniversary.setRemindDays(
				dto.remindDays() != null ? dto.remindDays() : DEFAULT_REMIND_DAYS);
		anniversary.setRemark(dto.remark() != null ? dto.remark().trim() : "");
	}

	/**
	 * 校验"月-日"这一对在日历上真实存在。
	 *
	 * <p>Bean Validation 的注解是逐字段独立的，表达不了"day 的上限随 month 变化"
	 * 这种依赖，所以只能写在 Service 里。不校验的话 4 月 31 日能存进去，
	 * 到算"下一次是哪天"时才暴露，那时数据已经脏了。
	 *
	 * <p>2 月用 {@link Month#maxLength()}（即 29）作上限：闰年出生的人真实存在，
	 * 不能因为平年没有 2/29 就拒绝录入。平年那次怎么算交给
	 * {@link YearlyRecurrence}，它会退到 2 月末。
	 */
	private void validateMonthDay(Integer month, Integer day) {
		if (month == null || day == null) {
			// 交给 DTO 上的 @NotNull 报错，这里不抢着报第二遍
			return;
		}
		if (day > Month.of(month).maxLength()) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					month + " 月没有 " + day + " 日");
		}
	}

	/**
	 * 取出属于当前用户的记录，不存在则 404。
	 *
	 * <p>{@code selectById} 会被拦截器补上 {@code user_id} 条件，拿别人的 id
	 * 必然返回 null。
	 *
	 * <p><b>返回 404 而不是 403</b>：403 等于确认"这个 id 确实存在，只是不归你"，
	 * 主键连续自增，据此能摸出全库的记录规模。理由与 {@code PlanTaskServiceImpl} 相同。
	 */
	private Anniversary requireOwned(Long id) {
		if (id == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
		}
		Anniversary anniversary = anniversaryMapper.selectById(id);
		if (anniversary == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "记录不存在");
		}
		return anniversary;
	}

}
