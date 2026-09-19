package org.example.workbenchserver.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * 密码编码器。
 *
 * <p>CLAUDE.md 要求密码必须加盐哈希，禁止明文或 MD5。BCrypt 自带随机盐，
 * 同一密码每次编码结果都不同（盐在哈希串里），因此比对必须用
 * {@link PasswordEncoder#matches} 而不是比较字符串相等。
 *
 * <p>这里只依赖 {@code spring-security-crypto}，没引完整的 Spring Security：
 * 那会带来一整套过滤器链和默认登录页，和项目自己的 JWT 拦截器打架。
 */
@Configuration
public class PasswordEncoderConfig {

	@Bean
	public PasswordEncoder passwordEncoder() {
		return new BCryptPasswordEncoder();
	}

}
