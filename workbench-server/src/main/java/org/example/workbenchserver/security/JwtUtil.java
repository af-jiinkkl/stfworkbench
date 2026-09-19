package org.example.workbenchserver.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.example.workbenchserver.config.WorkbenchProperties;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 签发与校验。
 *
 * <p>选 JWT 而不是服务端 session，是为了保住"后端是纯 API 服务"这条约定
 * （docs/需求说明.md §5）：将来加 App 时，token 放进请求头即可复用，
 * 而 session cookie 是绑定浏览器的。登出因此不需要接口 —— 前端丢掉 token 就算登出。
 *
 * <p><b>密钥来源</b>：环境变量 {@code JWT_SECRET}，没有默认值。没配或太短都在
 * 启动时直接失败，而不是悄悄退化成一个写死的弱密钥 —— 那种"能跑起来"的假象，
 * 意味着任何人都能自己签发 token 冒充任意用户。
 */
@Component
public class JwtUtil {

	private static final String CLAIM_USERNAME = "username";

	/**
	 * 密钥长度下限 32 字节。
	 *
	 * <p>这是 HMAC 家族里最弱的 HS256 的要求（256 位 = 32 字节）；
	 * 再短就没有任何算法可用，jjwt 会直接抛 WeakKeyException。
	 * 下限取 32 而非 48，是为了不让开发环境被迫配一个很长的密钥。
	 */
	private static final int MIN_SECRET_BYTES = 32;

	private final SecretKey key;

	private final long expireMillis;

	public JwtUtil(WorkbenchProperties properties) {
		String secret = properties.getJwt().getSecret();
		if (!StringUtils.hasText(secret)) {
			throw new IllegalStateException("""
					未配置 JWT 密钥，应用拒绝启动。
					请设置环境变量 JWT_SECRET（至少 32 个字符），例如：
					  export JWT_SECRET='本机开发用的随机长字符串至少三十二个字符'
					生产环境请换成一个真正随机且不入库的值。""");
		}

		byte[] secretBytes = secret.getBytes(StandardCharsets.UTF_8);
		if (secretBytes.length < MIN_SECRET_BYTES) {
			throw new IllegalStateException("JWT_SECRET 太短：当前 " + secretBytes.length
					+ " 字节，至少需要 " + MIN_SECRET_BYTES + " 字节。");
		}

		// 注意：具体用哪个算法由**密钥长度**决定，不是写死的 ——
		// hmacShaKeyFor 会挑密钥撑得住的最强算法：
		//   >= 64 字节 → HS512，>= 48 字节 → HS384，>= 32 字节 → HS256。
		// 开发用的 59 字节密钥因此签发的是 HS384（token 头里能看到 alg）。
		// 副作用是：换密钥长度会换算法，已签发的 token 会解析失败 ——
		// 生产环境换密钥本来就要所有人重新登录，可接受。
		this.key = Keys.hmacShaKeyFor(secretBytes);
		this.expireMillis = properties.getJwt().getExpireHours() * 3600_000L;
	}

	/**
	 * 签发 token。
	 *
	 * <p>sub 存用户 id 而不是用户名：用户名可以被改（且删除后可被他人重新注册），
	 * 而 id 是稳定且唯一的。用户名放进自定义 claim 仅供排查问题看，不作为身份依据。
	 */
	public String generate(Long userId, String username) {
		Date now = new Date();
		return Jwts.builder()
				.subject(String.valueOf(userId))
				.claim(CLAIM_USERNAME, username)
				.issuedAt(now)
				.expiration(new Date(now.getTime() + expireMillis))
				.signWith(key)
				.compact();
	}

	/**
	 * 解析并校验 token，返回用户 id。
	 *
	 * <p>签名不对、被篡改、已过期都会抛 {@code JwtException}，由调用方转成 401。
	 */
	public Long parseUserId(String token) {
		Claims claims = Jwts.parser()
				.verifyWith(key)
				.build()
				.parseSignedClaims(token)
				.getPayload();
		return Long.valueOf(claims.getSubject());
	}

}
