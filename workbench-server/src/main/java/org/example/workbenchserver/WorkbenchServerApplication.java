package org.example.workbenchserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 入口。
 *
 * <p><b>不要把 {@code @MapperScan} 加在这个类上。</b>它是测试切片的配置类
 * （{@code @JsonTest} / {@code @WebMvcTest} 会沿着包往上找到它），而切片不装配
 * MyBatis。一旦 {@code @MapperScan} 在这里，切片启动时就会去注册 Mapper，
 * 然后因为找不到 {@code SqlSessionFactory} 而失败，报出的却是一句
 * "Property 'sqlSessionFactory' or 'sqlSessionTemplate' are required"，
 * 和被测内容毫无关系。
 *
 * <p>所以 Mapper 扫描放在 {@code MybatisPlusConfig} 里 —— 那是个普通的
 * {@code @Configuration}，切片会把它排除掉。
 */
@SpringBootApplication
public class WorkbenchServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(WorkbenchServerApplication.class, args);
	}

}
