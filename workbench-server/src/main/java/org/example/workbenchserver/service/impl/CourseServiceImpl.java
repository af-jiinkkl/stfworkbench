package org.example.workbenchserver.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import org.example.workbenchserver.common.CourseTime;
import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.result.ResultCode;
import org.example.workbenchserver.dto.CourseDTO;
import org.example.workbenchserver.entity.Course;
import org.example.workbenchserver.entity.Semester;
import org.example.workbenchserver.mapper.CourseMapper;
import org.example.workbenchserver.mapper.SemesterMapper;
import org.example.workbenchserver.service.CourseService;
import org.example.workbenchserver.vo.CourseVO;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/**
 * 课程业务实现。
 *
 * <p><b>注意全类没有任何一处 {@code user_id} 条件</b>，这是刻意的：
 * 隔离由 {@code MybatisPlusConfig} 的租户插件在 SQL 生成阶段注入。
 * 手写反而会掩盖拦截器失效的问题。
 *
 * <p><b>但 {@code semester_id} 是另一回事。</b>租户插件管的是本表自己的
 * {@code user_id}，管不到这一列指向谁 —— 拿别人的学期 id 来建课，
 * 拦截器不会拦，库里会安静地多出一条指向别人学期的记录。
 * 所以每次写入前都要 {@code requireOwnedSemester}。
 */
@Service
public class CourseServiceImpl implements CourseService {

	private final CourseMapper courseMapper;

	private final SemesterMapper semesterMapper;

	public CourseServiceImpl(CourseMapper courseMapper, SemesterMapper semesterMapper) {
		this.courseMapper = courseMapper;
		this.semesterMapper = semesterMapper;
	}

	@Override
	public List<CourseVO> listBySemester(Long semesterId) {
		requireOwnedSemester(semesterId);

		return courseMapper.selectList(ordered(semesterId)).stream().map(CourseVO::from).toList();
	}

	@Override
	public List<CourseVO> listOnDate(LocalDate date) {
		if (date == null) {
			return List.of();
		}

		// 先找包含这一天的学期。学期总共就几条，全取出来在 Java 里筛，
		// 不为此写一条 start_date <= ? AND DATE_ADD(...) >= ? 的 SQL ——
		// 那条表达式的可读性很差，而这里的数据规模小到不值得。
		// 代价是这条查询的 WHERE 里**一个字都没有**（隔离和逻辑删除全靠拦截器），
		// 所以它和 AnniversaryServiceImpl#upcoming、MemoServiceImpl#count
		// 属于同一类，单独钉了用例（见 CourseIsolationTest）
		Optional<Semester> current = semesterMapper.selectList(null).stream()
				.filter(semester -> contains(semester, date))
				.findFirst();

		if (current.isEmpty()) {
			// 寒暑假、或者还没建学期。这是正常状态，不是错误 ——
			// 返回空列表让首页显示"今天没有课"
			return List.of();
		}

		Semester semester = current.get();
		int week = weekOf(semester, date);
		int dayOfWeek = date.getDayOfWeek().getValue();

		LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(Course::getSemesterId, semester.getId());
		wrapper.eq(Course::getDayOfWeek, dayOfWeek);
		wrapper.orderByAsc(Course::getStartSection).orderByAsc(Course::getId);

		// 单双周在 Java 里筛。这条判断只有一个权威来源（CourseTime#occursOnWeek），
		// 前端渲染课表时也用同一条规则 —— 两处各写一遍的话，
		// 会出现"课表上显示有、首页说今天没课"这种分叉
		return courseMapper.selectList(wrapper).stream()
				.filter(course -> CourseTime.occursOnWeek(
						course.getStartWeek(), course.getEndWeek(), course.getWeekType(), week))
				.map(CourseVO::from)
				.toList();
	}

	@Override
	public CourseVO create(CourseDTO dto) {
		Semester semester = requireOwnedSemester(dto.semesterId());
		requireValidRanges(dto, semester);

		Course course = new Course();
		// 刻意不设 user_id —— 实体上根本没有这个属性，见 Course 的类注释
		apply(course, dto);

		courseMapper.insert(course);
		// 回读一次：创建时间由数据库填，MyBatis-Plus 插完不会带回来
		return CourseVO.from(requireOwned(course.getId()));
	}

	@Override
	public CourseVO update(Long id, CourseDTO dto) {
		requireOwned(id);
		Semester semester = requireOwnedSemester(dto.semesterId());
		requireValidRanges(dto, semester);

		Course course = requireOwned(id);
		apply(course, dto);
		courseMapper.updateById(course);

		return CourseVO.from(requireOwned(id));
	}

	@Override
	public void delete(Long id) {
		// 先确认这条是本人的且存在，为的是能准确返回 404 ——
		// deleteById 本身也会被拦截器补 user_id 条件，删不到别人的
		requireOwned(id);
		courseMapper.deleteById(id);
	}

	/** 课表要的顺序：星期 → 节次 → id。最后一级是为了让同格的两条也有确定次序 */
	private LambdaQueryWrapper<Course> ordered(Long semesterId) {
		LambdaQueryWrapper<Course> wrapper = new LambdaQueryWrapper<>();
		wrapper.eq(Course::getSemesterId, semesterId);
		wrapper.orderByAsc(Course::getDayOfWeek)
				.orderByAsc(Course::getStartSection)
				.orderByAsc(Course::getId);
		return wrapper;
	}

	/**
	 * 这个学期是否包含这一天。
	 *
	 * <p>第 1 周的第一天就是 {@code startDate}（周一），最后一天是
	 * {@code startDate + totalWeeks*7 - 1}。**闭区间**，最后一周的最后一天
	 * 也算在内 —— 写成开区间的话，学期最后一天上的课会从首页消失。
	 */
	private static boolean contains(Semester semester, LocalDate date) {
		LocalDate first = semester.getStartDate();
		if (first == null || semester.getTotalWeeks() == null) {
			return false;
		}
		LocalDate last = first.plusDays((long) semester.getTotalWeeks() * 7 - 1);
		return !date.isBefore(first) && !date.isAfter(last);
	}

	/** 这一天是本学期的第几周，从 1 开始。调用前必须已确认日期落在学期内 */
	private static int weekOf(Semester semester, LocalDate date) {
		return (int) (ChronoUnit.DAYS.between(semester.getStartDate(), date) / 7) + 1;
	}

	/**
	 * 所有范围校验。
	 *
	 * <p>分两类，都在这里而不是 DTO 上：一类是**互相之间**的约束
	 * （节次前后、周次前后），单字段注解表达不了；另一类是**跨表**的
	 * （周次不能超过所属学期的总周数），要先查库才知道。
	 *
	 * <p>周次的上界用**所属学期的总周数**，不是 {@code CourseTime} 里的全局上限：
	 * 一个 16 周的学期里排一门"到第 18 周"的课，那条记录会存进库、
	 * 却永远不出现在课表的任何一格上 —— 它没被删，只是看不见了。
	 */
	private void requireValidRanges(CourseDTO dto, Semester semester) {
		if (!CourseTime.isValidDayOfWeek(dto.dayOfWeek())) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"星期几必须在 " + CourseTime.MIN_DAY_OF_WEEK + " 到 "
							+ CourseTime.MAX_DAY_OF_WEEK + " 之间");
		}

		if (!CourseTime.isValidSection(dto.startSection()) || !CourseTime.isValidSection(dto.endSection())) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"节次必须在 " + CourseTime.MIN_SECTION + " 到 " + CourseTime.MAX_SECTION + " 之间");
		}
		if (dto.startSection() > dto.endSection()) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"开始节次不能晚于结束节次");
		}

		if (!CourseTime.isValidWeekType(normalizeWeekType(dto.weekType()))) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"周类型只能是 0（每周）、1（单周）或 2（双周）");
		}

		int totalWeeks = semester.getTotalWeeks() != null ? semester.getTotalWeeks() : 0;
		if (dto.startWeek() == null || dto.startWeek() < 1) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "起始周必须从 1 开始");
		}
		if (dto.endWeek() == null || dto.endWeek() > totalWeeks) {
			throw new BusinessException(ResultCode.BAD_REQUEST,
					"结束周不能超过该学期的总周数（" + totalWeeks + " 周）");
		}
		if (dto.startWeek() > dto.endWeek()) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "起始周不能晚于结束周");
		}
	}

	private void apply(Course course, CourseDTO dto) {
		course.setSemesterId(dto.semesterId());
		course.setName(dto.name().trim());
		// 空值落成**空串而不是 null**，不能省：MyBatis-Plus 默认的字段更新策略
		// 是 NOT_NULL，updateById 会把 null 字段整条跳过。PUT 的语义是全量替换，
		// 若用户清空老师时传 null，那条 SET 会消失，旧值原封不动留在库里 ——
		// 界面上看着已清空，刷新一下又回来了（与备忘录、消费是同一个坑）
		course.setTeacher(trimToEmpty(dto.teacher()));
		course.setLocation(trimToEmpty(dto.location()));
		course.setDayOfWeek(dto.dayOfWeek());
		course.setStartSection(dto.startSection());
		course.setEndSection(dto.endSection());
		course.setStartWeek(dto.startWeek());
		course.setEndWeek(dto.endWeek());
		course.setWeekType(normalizeWeekType(dto.weekType()));
	}

	/** 不传周类型按"每周"处理。列有 DEFAULT 0，但实体上那份得自己填，否则 INSERT 写 null */
	private static int normalizeWeekType(Integer weekType) {
		return weekType != null ? weekType : CourseTime.WEEK_TYPE_EVERY;
	}

	/**
	 * 取出属于当前用户的课程，不存在则 404。
	 *
	 * <p>{@code selectById} 会被拦截器补上 {@code user_id} 条件，拿别人的 id
	 * 必然返回 null。<b>返回 404 而不是 403</b>，理由与其余模块相同：
	 * 403 等于确认该 id 存在，而主键连续自增，就成了存在性探测点。
	 */
	private Course requireOwned(Long id) {
		if (id == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "id 不能为空");
		}
		Course course = courseMapper.selectById(id);
		if (course == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
		}
		return course;
	}

	/**
	 * 取出属于当前用户的学期，不存在则 404。
	 *
	 * <p><b>这是本模块安全上最要紧的一处。</b>{@code semester_id} 只是个普通外键值，
	 * 没有任何数据库约束能保证它指向的学期属于同一个人 ——
	 * 租户插件管的是 {@code wb_course.user_id}，管不到 {@code semester_id} 指向谁。
	 *
	 * <p>少了这一步，越权写入虽然不会让别人看到什么（那条记录的 user_id 仍是写入者），
	 * 但库里会留下一条指向别人学期的悬空记录，而两边都看不到它 ——
	 * 一条谁也不认识、也不会报错的数据。用例见 {@code CourseIsolationTest}。
	 */
	private Semester requireOwnedSemester(Long semesterId) {
		if (semesterId == null) {
			throw new BusinessException(ResultCode.BAD_REQUEST, "学期不能为空");
		}
		Semester semester = semesterMapper.selectById(semesterId);
		if (semester == null) {
			throw new BusinessException(ResultCode.NOT_FOUND, "学期不存在");
		}
		return semester;
	}

	private static String trimToEmpty(String value) {
		return value != null ? value.trim() : "";
	}

}
