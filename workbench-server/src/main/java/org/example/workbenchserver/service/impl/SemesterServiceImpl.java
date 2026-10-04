package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.CourseTime;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.dto.SemesterDTO;
import org.example.workbenchserver.entity.Course;
import org.example.workbenchserver.entity.Semester;
import org.example.workbenchserver.mapper.CourseMapper;
import org.example.workbenchserver.mapper.SemesterMapper;
import org.example.workbenchserver.service.SemesterService;
import org.example.workbenchserver.vo.SemesterVO;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 学期业务实现。
 *
 * <p><b>注意全类没有任何一处 {@code user_id} 条件</b>，这是刻意的：
 * 隔离由 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段注入。
 * 手写反而会掩盖拦截器失效的问题。
 *
 * <p>本类同时依赖 {@link SemesterMapper} 和 {@link CourseMapper} ——
 * 两处"跨表的业务规则"都在这里：
 *
 * <ul>
 *   <li>删除学期前先数它下面有几门课（接口清单 §12 定的 B 方案）</li>
 *   <li>改小学期总周数前先看有没有课的周次会落到学期之外</li>
 * </ul>
 *
 * 这两条本质是同一件事：**宁可让用户多操作一步，也不要让课程数据
 * 在无人察觉的情况下失效**。第二条不校验的话，把 20 周改成 16 周之后，
 * 排在第 18 周的课既没被删、也不在任何一格上显示 —— 它还在库里，
 * 只是从此不见了。
 */
@Service
public class SemesterServiceImpl implements SemesterService {

	private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy-MM-dd");

	private final SemesterMapper semesterMapper;

	private final CourseMapper courseMapper;

	public SemesterServiceImpl(SemesterMapper semesterMapper, CourseMapper courseMapper) {
		this.semesterMapper = semesterMapper;
		this.courseMapper = courseMapper;
	}

	@Override
	public List<SemesterVO> list() {
		// 最近开始的排前面。不加 user_id 条件，隔离靠拦截器
		LambdaQueryWrapper<Semester> wrapper = new LambdaQueryWrapper<>();
		wrapper.orderByDesc(Semester::getStartDate).orderByDesc(Semester::getId);

		return semesterMapper.selectList(wrapper).stream().map(SemesterVO::from).toList();
	}

	@Override
	public SemesterVO create(SemesterDTO dto) {
		requireMonday(dto.startDate());
		requireWeeksWithinRange(dto.totalWeeks());

		Semester semester = new Semester();
		// 刻意不设 user_id —— 实体上根本没有这个属性，见 Semester 的类注释
		apply(semester, dto);

		semesterMapper.insert(semester);

		// 回读一次：create_time / update_time 是数据库的 DEFAULT CURRENT_TIMESTAMP
		// 填的，MyBatis-Plus 插完不会把生成的值带回实体（同 MemoServiceImpl#create）
		return SemesterVO.from(requireOwned(semester.getId()));
	}

	@Override
	public SemesterVO update(Long id, SemesterDTO dto) {
		Semester semester = requireOwned(id);
		requireMonday(dto.startDate());
		requireWeeksWithinRange(dto.totalWeeks());
		requireNoCourseBeyondWeeks(id, dto.totalWeeks());

		apply(semester, dto);
		semesterMapper.updateById(semester);

		// 同理：update_time 由 MySQL 的 ON UPDATE 维护，实体里那份还是上一次的
		return SemesterVO.from(requireOwned(id));
	}

	@Override
	public void delete(Long id) {
		// 先确认这个学期是本人的且存在，为的是能准确返回 404。
		// deleteById 本身也会被拦截器补 user_id 条件，删不到别人的 ——
		// 这一步是为了区分"删了"和"本来就没有"，不是为了安全
		requireOwned(id);

		Long courseCount = countCourses(id);
		if (courseCount != null && courseCount > 0) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"该学期下还有 " + courseCount + " 门课，请先删除这些课程");
		}

		semesterMapper.deleteById(id);
	}

	/**
	 * 数这个学期下有几门课。
	 *
	 * <p>用 {@code selectCount} 而不是手写 {@code COUNT(*)}：
	 * 条件里同样**一个字都没写 user_id**，隔离与逻辑删除都靠拦截器 ——
	 * 和 {@code MemoServiceImpl#count} 属于同一类"看不见条件"的查询，
	 * 所以那条路径单独钉了用例（见 {@code CourseIsolationTest}）。
	 *
	 * <p>返回 {@code Long} 且可能为 null：{@code selectCount} 在极端情况下
	 * 会返回 null，调用处挡一下，免得变成一个莫名其妙的 NPE。
	 */
	private Long countCourses(Long semesterId) {
		LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(Course::getSemesterId, semesterId);
		return courseMapper.selectCount(wrapper);
	}

	/**
	 * 改小学期总周数之前，确认没有课的周次会落到学期之外。
	 *
	 * <p><b>这条规则接口清单里没有写</b>，是照着 §12 那条"禁止删除有课程的学期"
	 * 的同一条思路补的：把一个 20 周的学期改成 16 周，排在第 18 周的课
	 * 既不会被删掉、也不会再出现在课表的任何一格上 —— 数据还在库里，
	 * 但从此看不见了，而用户只是改了个数字，没有任何提示。
	 *
	 * <p>提示里带上课程名和它排到的周数，否则用户不知道该改哪一门。
	 * 只列前几门，一门学期几十门课时提示会长得没法看。
	 */
	private void requireNoCourseBeyondWeeks(Long semesterId, int totalWeeks) {
		LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(Course::getSemesterId, semesterId);
		wrapper.gt(Course::getEndWeek, totalWeeks);
		wrapper.orderByDesc(Course::getEndWeek);

		List<Course> beyond = courseMapper.selectList(wrapper);
		if (beyond.isEmpty()) {
			return;
		}

		String names = beyond.stream()
				.limit(MAX_NAMES_IN_MESSAGE)
				.map(course -> "《" + course.getName() + "》到第 " + course.getEndWeek() + " 周")
				.collect(Collectors.joining("、"));
		String more = beyond.size() > MAX_NAMES_IN_MESSAGE
				? " 等 " + beyond.size() + " 门课"
				: "";

		throw new BusinessException(ResultCode.BAD_REQUEST,
				"总周数不能改成 " + totalWeeks + " 周：" + names + more
						+ "排在这个范围之外，请先调整这些课程");
	}

	/**
	 * 校验 {@code startDate} 是周一。
	 *
	 * <p><b>这是整张课表的地基，不能省。</b>第 N 周的星期几要靠
	 * {@code startDate + (N-1)*7 + (星期几-1)} 换算成真实日期，
	 * 基准若不是周一，整个学期的课表就整体偏几天 ——
	 * 而它显示出来**仍然是一张看着完全正常的课表**，没有任何一步会报错。
	 *
	 * <p>只报错、不替用户挪到最近的周一：用户选的日期被悄悄改掉，
	 * 比报错难查得多 —— 他下次打开时看到的是一个自己没选过的日期。
	 */
	private void requireMonday(LocalDate startDate) {
		if (startDate == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "开始日期不能为空");
		}
		if (startDate.getDayOfWeek() != DayOfWeek.MONDAY) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"开始日期必须是第 1 周的周一（" + startDate.format(DATE)
							+ " 是" + chineseDayOfWeek(startDate.getDayOfWeek()) + "）");
		}
	}

	/**
	 * 周数范围校验。
	 *
	 * <p>与 {@code SemesterDTO} 上的 {@code @Min/@Max} 用的是**同一组常量**
	 * （见 {@link CourseTime}）。DTO 那层是请求体的第一道闸，这里是第二道 ——
	 * 换一个入口进来时（比如将来的导入、或者别的 Service 直接调），
	 * 就没有 DTO 那层了。
	 *
	 * <p>不夹取而报错：把 200 悄悄夹成 60 会存下一个用户没要过的值，
	 * 而他只有等课表翻不到头了才会发现（同 {@code ExpenseServiceImpl#resolveYear}）。
	 */
	private void requireWeeksWithinRange(Integer totalWeeks) {
		if (totalWeeks == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "总周数不能为空");
		}
		if (totalWeeks < CourseTime.MIN_TOTAL_WEEKS || totalWeeks > CourseTime.MAX_TOTAL_WEEKS) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"总周数必须在 " + CourseTime.MIN_TOTAL_WEEKS + " 到 "
							+ CourseTime.MAX_TOTAL_WEEKS + " 之间");
		}
	}

	private void apply(Semester semester, SemesterDTO dto) {
		semester.setName(dto.name().trim());
		semester.setStartDate(dto.startDate());
		semester.setTotalWeeks(dto.totalWeeks());
	}

	/**
	 * 取出属于当前用户的学期，不存在则 404。
	 *
	 * <p>{@code selectById} 会被拦截器补上 {@code user_id} 条件，拿别人的 id
	 * 必然返回 null。<b>返回 404 而不是 403</b>：403 等于确认"这个 id 确实存在，
	 * 只是不归你"，主键连续自增，据此能摸出全库的记录规模。
	 */
	private Semester requireOwned(Long id) {
		if (id == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
		}
		Semester semester = semesterMapper.selectById(id);
		if (semester == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在");
		}
		return semester;
	}

	/** 提示里最多列几门课，免得一屏都是课程名 */
	private static final int MAX_NAMES_IN_MESSAGE = 3;

	private static String chineseDayOfWeek(DayOfWeek dayOfWeek) {
		return switch (dayOfWeek) {
			case MONDAY -> "周一";
			case TUESDAY -> "周二";
			case WEDNESDAY -> "周三";
			case THURSDAY -> "周四";
			case FRIDAY -> "周五";
			case SATURDAY -> "周六";
			case SUNDAY -> "周日";
		};
	}

}
