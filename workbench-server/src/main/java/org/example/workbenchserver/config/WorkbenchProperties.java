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

	private News news = new News();

	public Jwt getJwt() {
		return jwt;
	}

	public void setJwt(Jwt jwt) {
		this.jwt = jwt;
	}

	public News getNews() {
		return news;
	}

	public void setNews(News news) {
		this.news = news;
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

	/**
	 * 新闻抓取，见 docs/接口清单.md §8。
	 *
	 * <p>与 {@link Jwt} 的 {@code secret} 处理刻意相反：密钥缺了要**启动即失败**，
	 * appkey 缺了只跳过抓取。理由是失败的后果不同 —— 没有 JWT 密钥的应用
	 * 会发出谁都验不了的 token，属于"看起来在跑其实全错"；而没有新闻，
	 * 首页少一张卡片而已，接口照常返回空数组。为了一个可选的第三方数据源
	 * 让整个应用起不来，是把一个小功能的可用性绑到了全站上。
	 */
	public static class News {

		/** 聚合数据的 appkey。由环境变量 JUHE_NEWS_KEY 注入，未配置时定时任务跳过抓取 */
		private String appkey = "";

		/** 抓取周期。每小时第 7 分钟 —— 挑非整点，避开对方可能的高峰时段 */
		private String fetchCron = "0 7 * * * *";

		public String getAppkey() {
			return appkey;
		}

		public void setAppkey(String appkey) {
			this.appkey = appkey;
		}

		public String getFetchCron() {
			return fetchCron;
		}

		public void setFetchCron(String fetchCron) {
			this.fetchCron = fetchCron;
		}

	}

}
