package org.example.workbenchserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * 项目自有配置，对应 application.yml 里的 {@code workbench.*}。
 */
@Component
@ConfigurationProperties(prefix = "workbench")
public class WorkbenchProperties {

	private Jwt jwt = new Jwt();

	private Registration registration = new Registration();

	public Jwt getJwt() {
		return jwt;
	}

	public void setJwt(Jwt jwt) {
		this.jwt = jwt;
	}

	public Registration getRegistration() {
		return registration;
	}

	public void setRegistration(Registration registration) {
		this.registration = registration;
	}

	public static class Jwt {

		/** 签名密钥。没有默认值 —— 由环境变量 JWT_SECRET 注入，见 JwtUtil 的启动校验 */
		private String secret = "";

		private int expireHours = 168;

		public String getSecret() {
			return secret;
		}

		public void setSecret(String secret) {
			this.secret = secret;
		}

		public int getExpireHours() {
			return expireHours;
		}

		public void setExpireHours(int expireHours) {
			this.expireHours = expireHours;
		}

	}

	public static class Registration {

		/** 是否开放注册。见 docs/接口清单.md §12 已确认事项 2 */
		private boolean enabled = true;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

	}

}
