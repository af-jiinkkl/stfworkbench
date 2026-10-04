package org.example.workbenchserver.service;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.dto.PlanTaskCreateDTO;
import org.example.workbenchserver.security.UserContext;
import org.example.workbenchserver.vo.PlanTaskVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 每日计划的服务层用例，主线是**排序** —— 这条链路上的三条约定：
 *
 * <ol>
 *   <li>重排按下标写回 {@code 0..n-1}，且是真写进库（不是只在返回值里排了排）</li>
 *   <li>提交的 id 集合与当天不一致时必须**整批拒绝**，不能改一半</li>
 *   <li>重排之后再新增，新任务落在**末尾** —— 见 {@link #createAppendsToEndAfterReorder}</li>
 * </ol>
 *
 * <p>与 {@code PlanTaskIsolationTest} 分工不同：那边验"别人的数据碰不到"，
 * 这边验"自己的数据排得对不对"。排序算错的表现很隐蔽 —— 界面不报错，
 * 只是顺序在刷新之后变成了另一个样子。
 *
 * <p>需要真实 MySQL，且 {@code wb_plan_task} 表已由 {@code db/schema.sql} 建好。
 * 跑之前设置环境变量 {@code DB_PASSWORD} 与 {@code JWT_SECRET}。
 */
@SpringBootTest
class PlanTaskServiceTest {

	private static final long USER_ID = 7010L;

	/** 另一个用户，用来在"集合对不上"里放一个不属于自己的 id */
	private static final long OTHER_USER_ID = 7011L;

	/** 2099 年，真实数据不可能落在这天，用它把测试数据与生产数据隔开 */
	private static final LocalDate TEST_DATE = LocalDate.of(2099, 2, 1);

	@Autowired
	private PlanTaskService planTaskService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	void setUp() {
		cleanTestRows();
		UserContext.set(USER_ID);
	}

	@AfterEach
	void tearDown() {
		cleanTestRows();
		UserContext.clear();
	}

	/**
	 * 走原生 JDBC：MyBatis 的删除会被拦截器加上 {@code user_id} 条件，
	 * 清不掉另一个用户那行 —— 而本类正好会种别人的数据。
	 *
	 * <p>条件里同时带 {@code user_id} 与日期：本表在生产库里有真数据，
	 * 不能像探针表那样 TRUNCATE。
	 */
	private void cleanTestRows() {
		jdbcTemplate.update(
				"DELETE FROM `wb_plan_task` WHERE `user_id` IN (?, ?) AND `plan_date` = ?",
				USER_ID, OTHER_USER_ID, TEST_DATE);
	}

	/** 走接口新增，省得每个用例都 new 一遍 DTO */
	private PlanTaskVO create(String content) {
		return planTaskService.create(new PlanTaskCreateDTO(TEST_DATE, content, null));
	}

	/** 绕过 Mapper 直接种一条别人的任务，返回它的 id */
	private Long seedOtherUsersTask() {
		jdbcTemplate.update(
				"INSERT INTO `wb_plan_task` (`user_id`, `plan_date`, `content`, `completed`, `sort_order`)"
						+ " VALUES (?, ?, ?, 0, 0)",
				OTHER_USER_ID, TEST_DATE, "别人的任务");
		return jdbcTemplate.queryForObject(
				"SELECT `id` FROM `wb_plan_task` WHERE `user_id` = ? AND `plan_date` = ?",
				Long.class, OTHER_USER_ID, TEST_DATE);
	}

	/** 绕过 Mapper 直接读库，用来断言"库里到底存成什么样" */
	private Integer rawSortOrderOf(Long id) {
		return jdbcTemplate.queryForObject(
				"SELECT `sort_order` FROM `wb_plan_task` WHERE `id` = ?", Integer.class, id);
	}

	@Test
	@DisplayName("重排按下标写回 0..n-1，列表顺序随之改变")
	void reorderWritesPositionAsSortOrder() {
		PlanTaskVO first = create("甲");
		PlanTaskVO second = create("乙");
		PlanTaskVO third = create("丙");

		List<PlanTaskVO> reordered = planTaskService.reorder(TEST_DATE,
				List.of(third.id(), first.id(), second.id()));

		assertThat(reordered).extracting(PlanTaskVO::content).containsExactly("丙", "甲", "乙");
		assertThat(reordered).extracting(PlanTaskVO::sortOrder).containsExactly(0, 1, 2);

		// 也直接查库：只看返回值的话，一个"只在内存里排了排、压根没写回"的实现照样全绿
		assertThat(rawSortOrderOf(third.id())).isZero();
		assertThat(rawSortOrderOf(first.id())).isEqualTo(1);
		assertThat(rawSortOrderOf(second.id())).isEqualTo(2);

		// 再查一次接口，确认顺序是"存住的"而不只是这一次返回值里排好的
		assertThat(planTaskService.listByDate(TEST_DATE))
				.extracting(PlanTaskVO::content)
				.containsExactly("丙", "甲", "乙");
	}

	@Test
	@DisplayName("提交的 id 与当天集合对不上（少 / 多 / 重复）一律 400，且一条都不改")
	void reorderRejectsMismatchedIdSet() {
		PlanTaskVO first = create("甲");
		PlanTaskVO second = create("乙");
		Long otherUsersId = seedOtherUsersTask();

		// 少传一条
		assertThatThrownBy(() -> planTaskService.reorder(TEST_DATE, List.of(second.id())))
				.isInstanceOf(BusinessException.class)
				.hasMessageContaining("刷新");

		// 多传一个别人的 id
		assertThatThrownBy(() -> planTaskService.reorder(TEST_DATE,
				List.of(first.id(), second.id(), otherUsersId)))
				.isInstanceOf(BusinessException.class);

		// 自己的 id 重复
		assertThatThrownBy(() -> planTaskService.reorder(TEST_DATE,
				List.of(first.id(), first.id(), second.id())))
				.isInstanceOf(BusinessException.class);

		// 三次都被挡在写库之前 —— 顺序仍是新增时的先后
		assertThat(planTaskService.listByDate(TEST_DATE))
				.extracting(PlanTaskVO::content)
				.containsExactly("甲", "乙");
		// 别人那行也没被顺带动过
		assertThat(rawSortOrderOf(otherUsersId)).isZero();
	}

	/**
	 * 本次修掉的缺陷：{@code create} 原先把 {@code sortOrder} 写死成 {@code 0}，
	 * 靠"同值再按 id 升序"才**碰巧**等价于"追加到末尾"。
	 *
	 * <p>那要求当天所有任务的排序值全是 {@code 0}。一旦拖动引入了 {@code 0/1/2…}，
	 * 新任务带着 {@code 0} 进来，只因为 id 更大就排到第 2 位 —— 不是末尾，也不报错。
	 */
	@Test
	@DisplayName("重排之后再新增，新任务落在末尾而不是第 2 位")
	void createAppendsToEndAfterReorder() {
		PlanTaskVO first = create("甲");
		PlanTaskVO second = create("乙");
		PlanTaskVO third = create("丙");

		// 把丙拖到最前。这一步之后排序值不再"全是 0"，老实现的前提就没了
		planTaskService.reorder(TEST_DATE, List.of(third.id(), first.id(), second.id()));

		PlanTaskVO fourth = create("丁");

		assertThat(planTaskService.listByDate(TEST_DATE))
				.extracting(PlanTaskVO::content)
				.containsExactly("丙", "甲", "乙", "丁");
		// 末尾到底意味着什么，写死成数字：前面三条重排后是 0/1/2，新来的应当是 3
		assertThat(rawSortOrderOf(fourth.id())).isEqualTo(3);
	}

}
