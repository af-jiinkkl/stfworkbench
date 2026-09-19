package org.example.workbenchserver.config;

import org.springframework.boot.jackson.autoconfigure.JsonMapperBuilderCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ext.javatime.deser.LocalDateDeserializer;
import tools.jackson.databind.ext.javatime.deser.LocalDateTimeDeserializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateSerializer;
import tools.jackson.databind.ext.javatime.ser.LocalDateTimeSerializer;
import tools.jackson.databind.module.SimpleModule;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * 全局统一日期序列化格式，落地 docs/接口清单.md §1.5：
 * {@code yyyy-MM-dd} 与 {@code yyyy-MM-dd HH:mm:ss}，不使用时间戳。
 *
 * <p>为什么要在这里统一配，而不是各 DTO 上贴 {@code @JsonFormat}：
 * 需求要求"不许各写各的"。贴注解意味着新增一个字段就多一次漏贴的机会，
 * 而漏贴之后 Jackson 默认会输出 ISO 的 {@code 2026-09-18T14:30:00}（带 T），
 * 前端按约定解析就会失败。配置在一处，新增字段自动就是对的。
 *
 * <p>注意本项目是 Spring Boot 4，用的是 Jackson 3，包名与 Boot 3 不同：
 * <ul>
 *   <li>定制器是 {@link JsonMapperBuilderCustomizer}（Boot 3 是 Jackson2ObjectMapperBuilderCustomizer）</li>
 *   <li>databind 在 {@code tools.jackson.databind}（Jackson 2 是 {@code com.fasterxml.jackson.databind}）</li>
 *   <li>java.time 支持已内置在 databind 中，不再需要单独引 JavaTimeModule</li>
 *   <li>唯一的例外：注解包名两代一致，仍是 {@code com.fasterxml.jackson.annotation}</li>
 * </ul>
 *
 * <p>{@code jjwt-jackson} 会传递进来一个 Jackson 2（见 pom 中的依赖树），
 * 那是 jjwt 内部序列化 JWT 载荷用的，与 Web 层的 Jackson 3 互不干扰，
 * 但写业务代码时务必别误用 Jackson 2 的包。
 */
@Configuration
public class JacksonConfig {

	public static final String DATE_PATTERN = "yyyy-MM-dd";

	public static final String DATE_TIME_PATTERN = "yyyy-MM-dd HH:mm:ss";

	private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern(DATE_PATTERN);

	private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern(DATE_TIME_PATTERN);

	@Bean
	public JsonMapperBuilderCustomizer workbenchJavaTimeCustomizer() {
		SimpleModule module = new SimpleModule("workbench-java-time");

		// 出参：LocalDate -> "2026-09-18"，LocalDateTime -> "2026-09-18 14:30:00"
		module.addSerializer(LocalDate.class, new LocalDateSerializer(DATE_FORMATTER));
		module.addSerializer(LocalDateTime.class, new LocalDateTimeSerializer(DATE_TIME_FORMATTER));

		// 入参：前端也按同一格式回传，故反序列化必须用同一套 formatter
		module.addDeserializer(LocalDate.class, new LocalDateDeserializer(DATE_FORMATTER));
		module.addDeserializer(LocalDateTime.class, new LocalDateTimeDeserializer(DATE_TIME_FORMATTER));

		return builder -> builder.addModule(module);
	}

}
