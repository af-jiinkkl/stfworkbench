package org.example.workbenchserver.service;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.common.util.WorkbenchTime;
import org.example.workbenchserver.mapper.NewsMapper;
import org.example.workbenchserver.security.UserContext;
import org.example.workbenchserver.service.impl.NewsServiceImpl;
import org.example.workbenchserver.vo.NewsVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 新闻读取与抓取。需要真实 MySQL（见 db/schema.sql 的 {@code wb_news}）。
 *
 * <p><b>这个类是全套隔离用例的镜像。</b>其余七个模块的测试都在验"别人的数据不能漏进来"，
 * 而新闻是全局共享的一份缓存（需求说明 §3.5：所有用户看同一份），
 * 所以这里要验的恰恰相反 —— **两个用户必须看到同一份**，
 * 并且**在没有登录态时也能读写**。后者不是理论情况：
 * 抓取跑在 {@code NewsFetchJob} 的调度线程上，那里本来就没人登录。
 *
 * <p>最后那条是这里最值得写的一条：往 {@code wb_news} 上加用户维度（或者把
 * {@code MybatisPlusConfig#TABLES_WITHOUT_USER_ID} 里的 {@code wb_news} 删掉）
 * 会让整个定时抓取当场 401 —— 而它报的是"未登录"，
 * 看到那句话几乎不可能联想到定时任务。
 *
 * <p>数据准备用原生 {@code JdbcTemplate}（理由同 {@code PlanTaskIsolationTest}：
 * 走 Mapper 插的话，拦截器一坏就红在插入阶段，真正的断言压根没跑过）。
 * {@code refresh} 的用例则是**直接构造 Service**，塞一个假的 {@code NewsSourceClient} 进去 ——
 * 真去调聚合数据的话，用例既慢又不确定，配额还白白消耗掉。
 */
@SpringBootTest
class NewsServiceTest {

	/**
	 * 测试数据的标题前缀。清理按它做，**不按日期**：
	 * 这个库里真的有一个每小时跑的定时任务在往 {@code wb_news} 写东西，
	 * 按日期删会连真实新闻一起清掉。见 {@code cleanTestNews}。
	 */
	private static final String PREFIX = "[用例]新闻-";

	/** 固定的过去日期：用它种数据的用例，断言的就是自己种的那几条 */
	private static final LocalDate A_PAST_DAY = LocalDate.of(2020, 3, 5);

	@Autowired
	private NewsService newsService;

	@Autowired
	private NewsMapper newsMapper;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestNews() {
		// 只删本用例种的那些。标题里的 `[` 不是 LIKE 的通配符（只有 % 和 _ 是），
		// 所以这个前缀是字面匹配，不会误伤
		jdbcTemplate.update("DELETE FROM `wb_news` WHERE `title` LIKE ?", PREFIX + "%");
		UserContext.clear();
	}

	// ---------- 种数据的帮手 ----------

	/** 测试数据一律加前缀，方便按前缀清理 */
	private static String t(String name) {
		return PREFIX + name;
	}

	private void insertNews(String title, LocalDate fetchDate, LocalDateTime publishTime) {
		jdbcTemplate.update(
				"INSERT INTO `wb_news` (`title`, `source`, `url`, `publish_time`, `fetch_date`) "
						+ "VALUES (?, '', '', ?, ?)",
				title, publishTime, fetchDate);
	}

	private static NewsSourceClient.FetchedNews item(String title, LocalDateTime publishTime) {
		return new NewsSourceClient.FetchedNews(title, "来源", "https://example.com/x", publishTime);
	}

	/** 拿真实 Mapper + 假的来源，构造一个独立的 Service */
	private NewsService serviceWith(NewsSourceClient client) {
		return new NewsServiceImpl(newsMapper, client);
	}

	// ---------- 读取 ----------

	@Test
	@DisplayName("按抓取日期取数：别的日期的混不进来，按发布时间倒序")
	void listByDateFiltersAndOrders() {
		// 种的时候刻意打乱先后：最早上榜的排在最后一条种进去，
		// 这样"刚好插入顺序就是查询顺序"不会被误当成排序生效
		insertNews(t("早"), A_PAST_DAY, A_PAST_DAY.atTime(8, 0));
		insertNews(t("晚"), A_PAST_DAY, A_PAST_DAY.atTime(20, 0));
		insertNews(t("中"), A_PAST_DAY, A_PAST_DAY.atTime(12, 0));
		// 抓取日期是另一天 —— 哪怕发布时间就在同一天，也不该出现在这批里
		insertNews(t("别的日子"), A_PAST_DAY.plusDays(1), A_PAST_DAY.atTime(23, 0));

		// 倒序：最新的在最前。首页卡片读的就是这个顺序，
		// 反过来的话卡片顶上会是当天最旧的几条，而列表本身看着毫无异常
		assertThat(newsService.listByDate(A_PAST_DAY))
				.extracting(NewsVO::title)
				.containsExactly(t("晚"), t("中"), t("早"));
	}

	/**
	 * 没有发布时间的排**最后**。
	 *
	 * <p>这条靠的是 MySQL 在 {@code ORDER BY ... DESC} 时把 NULL 排在末尾。
	 * 若哪天改成 {@code ASC}、或者换成别的写法让 NULL 冒到最前面，
	 * 首页卡片顶上就是几条连时间都没有的新闻 —— 看着像排序坏了，
	 * 但列表本身是"对"的，没人会当成 bug 报上来。
	 */
	@Test
	@DisplayName("没有发布时间的排在最后，不会因为 NULL 冒到最前面")
	void itemsWithoutPublishTimeComeLast() {
		insertNews(t("无时间"), A_PAST_DAY, null);
		insertNews(t("有时间"), A_PAST_DAY, A_PAST_DAY.atTime(9, 0));

		assertThat(newsService.listByDate(A_PAST_DAY))
				.extracting(NewsVO::title)
				.containsExactly(t("有时间"), t("无时间"));
	}

	/** 那天没抓到东西时给空列表，不是 null、也不是错误（前端拿到 null 会在 .length 上炸） */
	@Test
	@DisplayName("那天没有数据时返回空列表")
	void emptyDayReturnsEmptyList() {
		assertThat(newsService.listByDate(LocalDate.of(2000, 1, 1))).isEmpty();
	}

	/**
	 * {@code latestToday} 就是 {@code listToday} 的前 N 条，顺序一致。
	 *
	 * <p>断言写成"与 {@code listToday} 的前缀相同"而不是写死标题：
	 * 这个方法问的是**今天**，而今天那张表里可能有定时任务真抓来的新闻，
	 * 写死标题就会依赖库里当时有什么。用前缀比较，无论今天有几条都成立。
	 */
	@Test
	@DisplayName("latestToday 取前 N 条，且与 listToday 同序")
	void latestTodayIsPrefixOfListToday() {
		for (int i = 1; i <= 7; i++) {
			insertNews(t("第" + i + "条"), WorkbenchTime.today(),
					WorkbenchTime.today().atTime(6, 0).plusMinutes(i));
		}

		List<NewsVO> all = newsService.listToday();
		// 前面那 7 条保证今天至少有 7 条，取 5 条一定是取满的
		assertThat(all).hasSizeGreaterThanOrEqualTo(7);
		assertThat(newsService.latestToday(5)).isEqualTo(all.subList(0, 5));
	}

	/** 边界：0 与负数给空列表，不是"全部" —— 这类入参夹取在本仓库一律自己兜住 */
	@Test
	@DisplayName("latestToday(0) 与负数都是空列表，不是全部")
	void latestTodayWithNonPositiveLimitIsEmpty() {
		insertNews(t("有一条"), WorkbenchTime.today(), WorkbenchTime.today().atTime(6, 0));

		assertThat(newsService.latestToday(0)).isEmpty();
		assertThat(newsService.latestToday(-1)).isEmpty();
	}

	/**
	 * <b>这条是本类的重点：这个模块刻意不做隔离。</b>
	 *
	 * <p>与其余七个模块的用例方向相反 —— 那边验"别人的不能漏进来"，
	 * 这里验"两个用户看到的必须是同一份"。新闻是全局缓存，
	 * 一旦有人给它补上 user_id 条件（或者加了 user_id 列），
	 * 表现是"每个用户只看到自己抓的那几条"——而抓取是全局的，
	 * 于是谁都没有新闻，且不报错。
	 */
	@Test
	@DisplayName("两个用户看到的是同一份新闻，且与登录态无关")
	void newsIsSharedAcrossUsers() {
		insertNews(t("共享的"), A_PAST_DAY, A_PAST_DAY.atTime(10, 0));

		UserContext.set(3001L);
		assertThat(newsService.listByDate(A_PAST_DAY))
				.extracting(NewsVO::title).containsExactly(t("共享的"));

		UserContext.set(3002L);
		assertThat(newsService.listByDate(A_PAST_DAY))
				.extracting(NewsVO::title).containsExactly(t("共享的"));
	}

	// ---------- 抓取 ----------

	/**
	 * 抓取在没有登录态下照样跑通 —— **这就是定时任务的真实处境**。
	 *
	 * <p>{@code NewsFetchJob} 跑在调度线程上，{@code UserContext} 是空的。
	 * 而租户插件取当前用户时是**抛 401** 的，不是"取不到就不过滤"。
	 * 所以这条一旦红，红的会是 {@code 未登录}，而真正的原因是
	 * {@code wb_news} 没被排除在隔离之外 —— 看到"未登录"很难联想到定时任务。
	 *
	 * <p>顺带确认真的落盘了（查库而不是只看返回值）：{@code refresh} 的返回值
	 * 是它自己数的，光看它证明不了 INSERT 成功。
	 *
	 * <p><b>已验证过它逮得住。</b>把 {@code wb_news} 从
	 * {@code TABLES_WITHOUT_USER_ID} 里摘掉跑一遍，本类 12 条里 10 条当场变红，
	 * 而且**红成两种不同的样子**，两种都不好认：
	 * <ul>
	 *   <li>这条（没登录态）报的是
	 *       {@code MyBatisSystemException: ... Cause: BusinessException: 未登录或登录已过期}，
	 *       异常栈的顶端指向 {@code NewsServiceImpl.existingTitlesOn} ——
	 *       看着像抓取逻辑里做了什么需要登录的事，实际是表没被排除</li>
	 *   <li>另外那些设了 {@code UserContext} 的报 {@code BadSqlGrammar}，
	 *       因为拦截器往一张没有 {@code user_id} 列的表上拼了条件 ——
	 *       报的是"SQL 写错了"，而 SQL 一个字都没改</li>
	 * </ul>
	 */
	@Test
	@DisplayName("没有登录态也能抓取写入 —— 定时任务就是这么跑的")
	void refreshWorksWithoutLoginContext() {
		UserContext.clear();

		NewsService service = serviceWith(() -> List.of(
				item(t("甲"), LocalDateTime.of(2026, 9, 21, 8, 0)),
				item(t("乙"), null)));

		assertThat(service.refresh()).isEqualTo(2);
		assertThat(countTodayWithPrefix()).isEqualTo(2);
	}

	/**
	 * 抓取是幂等的：同一条新闻反复抓只写一次。
	 *
	 * <p><b>这件事不能省。</b>任务每小时跑一次，而第三方的头条榜几个小时内基本不变 ——
	 * 不去重的话同一条新闻一天被写 24 遍，列表上全是重复条目，表也白白长大。
	 */
	@Test
	@DisplayName("同一条新闻重复抓取只写一次")
	void refreshIsIdempotent() {
		NewsService service = serviceWith(() -> List.of(
				item(t("重复的"), LocalDateTime.of(2026, 9, 21, 8, 0))));

		assertThat(service.refresh()).isEqualTo(1);
		// 第二次：标题已经在今天的库里的，写 0 条
		assertThat(service.refresh()).isZero();

		assertThat(countTodayWithPrefix()).isEqualTo(1);
	}

	/** 同一次抓取内部自己重复的（对方偶尔会回两条一样的），也只该写一条 */
	@Test
	@DisplayName("同一次抓取里重复的标题只写一条")
	void refreshDeduplicatesWithinOneBatch() {
		NewsService service = serviceWith(() -> List.of(
				item(t("同批重复"), null),
				item(t("同批重复"), null)));

		assertThat(service.refresh()).isEqualTo(1);
		assertThat(countTodayWithPrefix()).isEqualTo(1);
	}

	/** 第三方没有数据是**正常情况**，写 0 条正常返回，不是异常 */
	@Test
	@DisplayName("第三方没有数据时写 0 条并正常返回")
	void refreshWithNoDataWritesNothing() {
		assertThat(serviceWith(() -> List.of()).refresh()).isZero();
	}

	/**
	 * 第三方报错时**原样抛出去**，由 {@code NewsFetchJob} 接住。
	 *
	 * <p>这里钉的是职责边界：Service 不吞异常（吞了的话，
	 * "appkey 过期"和"今天确实没新闻"在调用方看来一模一样），
	 * 而 Job 那边必须接住（不接住的话，每天的堆栈会把日志淹掉）。
	 * 两边各一条用例，改哪边都会红。
	 */
	@Test
	@DisplayName("第三方报错时原样抛出，由定时任务去接")
	void refreshPropagatesSourceFailure() {
		NewsService service = serviceWith(() -> {
			throw new BusinessException("新闻接口返回错误：请求次数超过限额（error_code=10012）");
		});

		assertThatThrownBy(service::refresh)
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("超过限额");
	}

	/**
	 * 超长标题**截断**而不是丢掉这一条。
	 *
	 * <p>MySQL 严格模式下，超长会让**整条 INSERT** 被拒 —— 为了一条标题
	 * 让整批抓取失败是最不划算的选择。而丢掉这一条的话，
	 * 它在界面上就是彻底没有。
	 */
	@Test
	@DisplayName("超长标题截断到 255，不是丢掉这一条")
	void refreshTruncatesOverlongTitle() {
		String longTitle = PREFIX + "长".repeat(300);
		NewsService service = serviceWith(() -> List.of(item(longTitle, null)));

		assertThat(service.refresh()).isEqualTo(1);

		String stored = jdbcTemplate.queryForObject(
				"SELECT `title` FROM `wb_news` WHERE `fetch_date` = ? AND `title` LIKE ?",
				String.class, WorkbenchTime.today(), PREFIX + "%");

		assertThat(stored).hasSize(255);
		// 截的是尾巴：前缀还在，说明留下的是开头那一段
		assertThat(stored).startsWith(PREFIX);
	}

	private int countTodayWithPrefix() {
		return jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM `wb_news` WHERE `title` LIKE ? AND `fetch_date` = ?",
				Integer.class, PREFIX + "%", WorkbenchTime.today());
	}

}
