package org.example.workbenchserver.common;

/**
 * 课程表的时间口径：星期、节次、周次、周类型的取值范围。
 *
 * <p>见 docs/数据模型.md §4.8 与 docs/接口清单.md §11。
 *
 * <p><b>值得单独成一个类型，是因为这些数字有且只有一个权威来源。</b>
 * 它们要在至少四个地方出现：DTO 的 {@code @Min/@Max} 注解、Service 的校验、
 * 错误提示里的文案、以及前端那份照着后端写的清单。前三个散在各自文件里时，
 * 迟早会出现"校验拦到 6、提示写着 8"这种不一致 —— 而它的表现是
 * 某个合法值被拒、或某个非法值被存进库，两种都要等用户碰上才发现。
 *
 * <p><b>关于注解引用常量</b>：{@code @Max(CourseTime.MAX_TOTAL_WEEKS)}
 * 是合法的，因为它们是编译期常量。所以 DTO 那层不必把数字再抄一遍。
 *
 * <p><b>前端的清单是另一回事</b>：它跨语言，没法引用这里的常量，
 * 只能在 {@code src/types/course.ts} 里照抄一份。这一份刻意不通过接口下发 ——
 * 理由与 {@link ExpenseCategory} 相同，见那个类的注释。
 */
public final class CourseTime {

	private CourseTime() {
	}

	// ---------- 星期 ----------

	/** 星期几的最小值。**1 是周一**，与 {@code java.time.DayOfWeek} 一致（它的 MONDAY 就是 1） */
	public static final int MIN_DAY_OF_WEEK = 1;

	/** 星期几的最大值，7 是周日 */
	public static final int MAX_DAY_OF_WEEK = 7;

	// ---------- 节次 ----------

	/**
	 * 一天最少几节。
	 *
	 * <p>需求定的是**一天 6 节**，所以这里最小值就是 1、最大值就是 6，
	 * 没有"不同学校节次不同"的余地。要放开的话，改这两个数字即可 ——
	 * 这也是把它们集中到一处的意义。
	 */
	public static final int MIN_SECTION = 1;

	/** 一天最多几节 */
	public static final int MAX_SECTION = 6;

	// ---------- 周次 ----------

	/** 一学期至少几周 */
	public static final int MIN_TOTAL_WEEKS = 1;

	/**
	 * 一学期最多几周。
	 *
	 * <p>列是 {@code TINYINT}（上限 127），这里收得更紧是照现实来的：
	 * 一学期十几到二十几周，60 已经给足余量。取 127 会让"手滑多打一位"
	 * 也照样存进去，而那样一条记录在课表上表现为"这个学期看起来正常，
	 * 只是永远翻不到头"。
	 */
	public static final int MAX_TOTAL_WEEKS = 60;

	// ---------- 周类型 ----------

	/** 每周 */
	public static final int WEEK_TYPE_EVERY = 0;

	/** 单周 */
	public static final int WEEK_TYPE_ODD = 1;

	/** 双周 */
	public static final int WEEK_TYPE_EVEN = 2;

	public static boolean isValidDayOfWeek(Integer dayOfWeek) {
		return dayOfWeek != null && dayOfWeek >= MIN_DAY_OF_WEEK && dayOfWeek <= MAX_DAY_OF_WEEK;
	}

	public static boolean isValidSection(Integer section) {
		return section != null && section >= MIN_SECTION && section <= MAX_SECTION;
	}

	public static boolean isValidWeekType(Integer weekType) {
		return weekType != null
				&& weekType >= WEEK_TYPE_EVERY && weekType <= WEEK_TYPE_EVEN;
	}

	/**
	 * 判断一门课在第 {@code week} 周上不上。
	 *
	 * <p><b>单双周是相对"学期的第几周"而言的，不是自然周的奇偶</b> ——
	 * 拿日历周的周数去判会在学期跨年时整体反过来。这里只收"第几周"，
	 * 从类型上就堵住了传错的那种写法。
	 *
	 * <p>放在后端而不是只写前端一份：首页的"今天有什么课"也要用同一条判断。
	 * 两处各写一遍，迟早出现"课表上显示有、首页说没有"这种
	 * 没人会当成 bug 报上来的分叉（同 {@code YearlyRecurrence} 的理由）。
     *
	 * @param week 学期第几周，从 1 开始
	 */
	public static boolean occursOnWeek(int startWeek, int endWeek, int weekType, int week) {
		if (week < startWeek || week > endWeek) {
			return false;
		}
		if (weekType == WEEK_TYPE_EVERY) {
			return true;
		}
		boolean oddWeek = week % 2 == 1;
		return weekType == WEEK_TYPE_ODD ? oddWeek : !oddWeek;
	}

	/** 周类型的可读文案，给错误提示用。从常量推出而不是另写一份 */
	public static String weekTypeLabel(Integer weekType) {
		if (weekType == null) {
			return "未知";
		}
		return switch (weekType) {
			case WEEK_TYPE_EVERY -> "每周";
			case WEEK_TYPE_ODD -> "单周";
			case WEEK_TYPE_EVEN -> "双周";
			default -> "未知";
		};
	}

}
