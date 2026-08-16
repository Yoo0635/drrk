package com.drrk.main.auth;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "auth")
public record AuthProperties(
		String jwtSecret,
		long accessTokenTtlMinutes,
		long refreshTokenTtlDays,
		Cookie cookie,
		List<String> corsAllowedOrigins
) {

	public Duration accessTokenTtl() {
		return Duration.ofMinutes(accessTokenTtlMinutes);
	}

	public Duration refreshTokenTtl() {
		return Duration.ofDays(refreshTokenTtlDays);
	}

	public record Cookie(
			boolean secure,
			String sameSite,
			String domain
	) {
	}
}
