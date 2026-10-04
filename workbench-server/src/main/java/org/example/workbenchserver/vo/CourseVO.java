package org.example.workbenchserver.vo;

import org.example.workbenchserver.entity.Course;

/**
 * 课程出参，字段与 docs/接口清单.md §11 的示例一一对应。
 *
 * <p><b>不带 {@code createTime} / {@code updateTime}</b>，与别的模块不一样。
 * 课表上每一格显示的是课名、老师、地点、周次，没有一处用得上"这条记录什么时候建的"；
 * 而多两个字段就多两处前端要忽略的东西。接口清单 §11 的示例里也没有它们。
 *
 * <p><b>不返回"时段"（早晨/上午/下午/晚上）</b>，虽然它由节次唯一决定 ——
 * 那是一个纯粹的显示分组，前端按节次推一下就有了。后端返回它等于给
 * "上午"这个说法留了两个定义，而两处不一致时没人会当成 bug 报上来。
 *
 * @param id           主键
 * @param semesterId   所属学期
 * @param name         课程名
 * @param teacher      任课老师，可能为空串，不会是 null
 * @param location     上课地点，可能为空串，不会是 null
 * @param dayOfWeek    星期几 1-7，1 是周一
 * @param startSection 开始节次 1-6
 * @param endSection   结束节次 1-6，不小于 startSection
 * @param startWeek    起始周
 * @param endWeek      结束周，不超过所属学期的总周数
 * @param weekType     0 每周 / 1 单周 / 2 双周。单双周是相对**学期第几周**而言的
 */
public record CourseVO(Long id, Long semesterId, String name, String teacher, String location,
		Integer dayOfWeek, Integer startSection, Integer endSection,
		Integer startWeek, Integer endWeek, Integer weekType) {

	public static CourseVO from(Course course) {
		return new CourseVO(
				course.getId(),
				course.getSemesterId(),
				course.getName(),
				// 建表脚本里这两列 NOT NULL DEFAULT ''，理论上取不出 null。
				// 兜一下是为了 VO 的契约能说"绝不会是 null" ——
				// 前端少写一个 ?? 就少一处会显示成 "null" 的地方（同 ExpenseVO）
				course.getTeacher() != null ? course.getTeacher() : "",
				course.getLocation() != null ? course.getLocation() : "",
				course.getDayOfWeek(),
				course.getStartSection(),
				course.getEndSection(),
				course.getStartWeek(),
				course.getEndWeek(),
				course.getWeekType());
	}

}
