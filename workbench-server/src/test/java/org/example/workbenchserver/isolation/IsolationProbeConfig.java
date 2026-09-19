package org.example.workbenchserver.isolation;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.test.context.TestConfiguration;

/**
 * 只为 {@link IsolationProbeMapper} 注册扫描的测试配置。
 *
 * <p><b>为什么探针不放在生产代码的 {@code org.example.workbenchserver.mapper} 包里：</b>
 * 启动类上的 {@code @MapperScan("org.example.workbenchserver.mapper")} 会在**每个**
 * Spring 测试里生效，包括 {@code @JsonTest} 这种只装配 Jackson、不装配 MyBatis 的切片。
 * 探针一旦落进那个包，{@code @JsonTest} 就会因为找不到 SqlSessionFactory 而启动失败
 * （报错信息是 "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"，
 * 看上去和被测的 Jackson 配置毫无关系，很容易误判成日期格式写错了）。
 *
 * <p>用 {@code @TestConfiguration} 而非 {@code @Configuration}：前者不进组件扫描，
 * 必须显式 {@code @Import} 才生效，避免这个 @MapperScan 蔓延到其他测试。
 */
@TestConfiguration
@MapperScan("org.example.workbenchserver.isolation")
public class IsolationProbeConfig {

}
