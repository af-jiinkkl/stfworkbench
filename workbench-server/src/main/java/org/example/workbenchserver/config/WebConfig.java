package org.example.workbenchserver.config;

import org.example.workbenchserver.security.JwtInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Web 层配置：注册认证拦截器。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

	private final JwtInterceptor jwtInterceptor;

	public WebConfig(JwtInterceptor jwtInterceptor) {
		this.jwtInterceptor = jwtInterceptor;
	}

	@Override
	public void addInterceptors(InterceptorRegistry registry) {
		registry.addInterceptor(jwtInterceptor)
				// 用白名单以外的全拦（"/api/**" 减去放行项），而不是逐个列出要保护的接口。
				// 后者意味着新增接口时忘了加进来就等于裸奔，这个方向不能搞反。
				.addPathPatterns("/api/**")
				.excludePathPatterns(
						"/api/auth/register",
						"/api/auth/login");
	}

}
