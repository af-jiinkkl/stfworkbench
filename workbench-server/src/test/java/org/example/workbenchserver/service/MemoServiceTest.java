package org.example.workbenchserver.service;

import org.example.workbenchserver.common.exception.BusinessException;
import org.example.workbenchserver.dto.MemoDTO;
import org.example.workbenchserver.security.UserContext;
import org.example.workbenchserver.vo.MemoDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 备忘录 Service 层的行为用例。
 *
 * <p>和 {@code MemoIsolationTest} 分工不同：那边验"看不到别人的数据"，
 * 这边验"自己的数据长什么样"。之所以专门建一个，是因为下面第一条曾经真的错过 ——
 * 隔离测试全绿、端到端脚本才逮到。
 *
 * <p>需要真实 MySQL，且 {@code wb_memo} 表已建好（见 db/schema.sql）。
 */
@SpringBootTest
class MemoServiceTest {

	/** 合成用户 id，远离真实用户规模，可按 user_id 精确清理 */
	private static final long USER_ID = 3010L;

	@Autowired
	private MemoService memoService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@BeforeEach
	@AfterEach
	void cleanTestUser() {
		jdbcTemplate.update("DELETE FROM `wb_memo` WHERE `user_id` = ?", USER_ID);
		UserContext.clear();
	}

	/**
	 * <b>写路径返回的时间戳不能是 null。</b>
	 *
	 * <p>{@code create_time} / {@code update_time} 由数据库的
	 * {@code DEFAULT CURRENT_TIMESTAMP} 填，MyBatis-Plus 插完不会把生成的值
	 * 带回实体。曾经就是直接把实体转 VO 返回，于是 POST 的响应里这两个字段是 null，
	 * 而紧接着 GET 同一个 id 却有值 —— 同一个对象两个接口两副面孔。
	 *
	 * <p>断言里连格式一起钉死（{@code yyyy-MM-dd HH:mm:ss}）：这是文档写明的对外
	 * 契约，一旦 Jackson 配置被改动、序列化成数组或时间戳，这条会先红。
	 */
	@Test
	@DisplayName("新增返回的创建/更新时间已填好，且是约定格式")
	void createReturnsTimestamps() {
		UserContext.set(USER_ID);

		MemoDetailVO created = memoService.create(new MemoDTO("标题", "正文"));

		assertThat(created.createTime()).isNotNull();
		assertThat(created.updateTime()).isNotNull();
		assertThat(created.createTime().format(java.time.format.DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")))
				.matches("\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2}");
	}

	/** 同一条记录，POST 拿到什么、GET 就该拿到什么 */
	@Test
	@DisplayName("新增的返回与随后查详情的结果一致")
	void createResponseMatchesDetail() {
		UserContext.set(USER_ID);

		MemoDetailVO created = memoService.create(new MemoDTO("标题", "正文"));
		MemoDetailVO fetched = memoService.detail(created.id());

		assertThat(created.createTime()).isEqualTo(fetched.createTime());
		assertThat(created.updateTime()).isEqualTo(fetched.updateTime());
	}

	@Test
	@DisplayName("修改返回的更新时间不为空，且不早于创建时间")
	void updateReturnsTimestamps() {
		UserContext.set(USER_ID);

		MemoDetailVO created = memoService.create(new MemoDTO("标题", "正文"));
		MemoDetailVO updated = memoService.update(created.id(), new MemoDTO("改了", "也改了"));

		assertThat(updated.createTime()).isNotNull();
		assertThat(updated.updateTime()).isNotNull();
		// DateTime 只到秒，同一秒内改完两者会相等，所以用 isAfterOrEqualTo
		assertThat(updated.updateTime()).isAfterOrEqualTo(updated.createTime());
	}

	/**
	 * 空值落成空串而不是 null。
	 *
	 * <p>后半件不是洁癖：MyBatis-Plus 默认的字段更新策略是 {@code NOT_NULL}，
	 * 传 null 的话那条 SET 子句整个消失，旧正文原样留在库里 ——
	 * 界面上看着已清空，刷新一下又回来了。
	 */
	@Test
	@DisplayName("入参为 null 时落成空串，且真的写进了库")
	void nullInputBecomesEmptyStringAndPersists() {
		UserContext.set(USER_ID);

		MemoDetailVO created = memoService.create(new MemoDTO("标题", "一段正文"));
		MemoDetailVO updated = memoService.update(created.id(), new MemoDTO("标题", null));

		assertThat(updated.content()).isEmpty();

		// 关键的一步：不信返回值，重新查一次库
		assertThat(memoService.detail(created.id()).content()).isEmpty();
	}

	@Test
	@DisplayName("首尾空格会被去掉")
	void trimsWhitespace() {
		UserContext.set(USER_ID);

		MemoDetailVO created = memoService.create(new MemoDTO("  标题  ", "  正文  "));

		assertThat(created.title()).isEqualTo("标题");
		assertThat(created.content()).isEqualTo("正文");
	}

	/**
	 * 标题和正文不能都为空。
	 *
	 * <p>Bean Validation 表达不了这种跨字段约束，只能写在 Service 里。
	 * 全都放行的话，列表里会出现一行什么都没有的空白，点进去也是空白。
	 */
	@Test
	@DisplayName("标题和正文都是空白 → 业务异常")
	void rejectsBlankTitleAndContent() {
		UserContext.set(USER_ID);

		assertThatThrownBy(() -> memoService.create(new MemoDTO("   ", "\n")))
				.isInstanceOf(BusinessException.class)
				.hasMessage("标题和正文不能都为空");
	}

	/** 校验用的边界：只有标题、或只有正文，都是合法的 */
	@Test
	@DisplayName("只写标题或只写正文都允许")
	void allowsEitherFieldAlone() {
		UserContext.set(USER_ID);

		assertThat(memoService.create(new MemoDTO("只有标题", "")).title()).isEqualTo("只有标题");
		assertThat(memoService.create(new MemoDTO("", "只有正文")).content()).isEqualTo("只有正文");
	}

}
