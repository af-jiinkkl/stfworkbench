package org.example.workbenchserver.config;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.json.JsonTest;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.json.JsonMapper;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 验证 docs/接口清单.md §1.5 的日期格式约定确实生效。
 *
 * <p>用 {@code @JsonTest} 而不是 {@code @SpringBootTest}：
 * 前者只装配 Jackson 相关组件，不碰数据源，因此跑测试不需要 MySQL 在跑。
 *
 * <p>{@code @Import(JacksonConfig.class)} 是必需的：测试切片不会扫描普通
 * {@code @Configuration} 类，不显式导入的话这个定制器根本不会生效，测试会假通过。
 */
@JsonTest
@Import(JacksonConfig.class)
class JacksonConfigTest {

	@Autowired
	private JsonMapper jsonMapper;

	record Sample(LocalDate date, LocalDateTime dateTime, String nullField) {
	}

	@Test
	void serializesDateWithUnifiedFormat() throws Exception {
		Sample sample = new Sample(LocalDate.of(2026, 9, 18), LocalDateTime.of(2026, 9, 18, 14, 30, 0), null);

		String json = jsonMapper.writeValueAsString(sample);

		assertThat(json).contains("\"date\":\"2026-09-18\"");
		assertThat(json).contains("\"dateTime\":\"2026-09-18 14:30:00\"");
	}

	@Test
	void deserializesDateWithUnifiedFormat() throws Exception {
		Sample sample = jsonMapper.readValue(
				"{\"date\":\"2026-09-18\",\"dateTime\":\"2026-09-18 14:30:00\"}", Sample.class);

		assertThat(sample.date()).isEqualTo(LocalDate.of(2026, 9, 18));
		assertThat(sample.dateTime()).isEqualTo(LocalDateTime.of(2026, 9, 18, 14, 30, 0));
	}

	/**
	 * 接口清单里 completedTime 未完成时是显式返回 null 的，
	 * 若配了 default-property-inclusion=non_null，字段会整个消失，
	 * 前端拿到的是 undefined 而不是 null。这条测试盯着这个坑。
	 */
	@Test
	void keepsNullFieldsInsteadOfOmittingThem() throws Exception {
		String json = jsonMapper.writeValueAsString(new Sample(null, null, null));

		assertThat(json).contains("\"nullField\":null");
	}

}
