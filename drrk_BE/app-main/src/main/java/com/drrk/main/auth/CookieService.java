package com.drrk.main.auth;

import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class CookieService {

	public static final String ACCESS_TOKEN_COOKIE = "access_token";
	public static final String REFRESH_TOKEN_COOKIE = "refresh_token";

	private final AuthProperties properties;

	public CookieService(AuthProperties properties) {
		this.properties = properties;
	}

	public void addAuthCookies(HttpServletResponse response, String accessToken, String refreshToken) {
		addCookie(response, ACCESS_TOKEN_COOKIE, accessToken, "/", properties.accessTokenTtl(), true);
		addCookie(response, REFRESH_TOKEN_COOKIE, refreshToken, "/api/v1/auth", properties.refreshTokenTtl(), true);
	}

	public void clearAuthCookies(HttpServletResponse response) {
		addCookie(response, ACCESS_TOKEN_COOKIE, "", "/", Duration.ZERO, true);
		addCookie(response, REFRESH_TOKEN_COOKIE, "", "/api/v1/auth", Duration.ZERO, true);
	}

	private void addCookie(
			HttpServletResponse response,
			String name,
			String value,
			String path,
			Duration maxAge,
			boolean httpOnly
	) {
		ResponseCookie.ResponseCookieBuilder builder = ResponseCookie.from(name, value)
				.httpOnly(httpOnly)
				.secure(properties.cookie().secure())
				.sameSite(properties.cookie().sameSite())
				.path(path)
				.maxAge(maxAge);
		if (properties.cookie().domain() != null && !properties.cookie().domain().isBlank()) {
			builder.domain(properties.cookie().domain());
		}
		response.addHeader("Set-Cookie", builder.build().toString());
	}
}
