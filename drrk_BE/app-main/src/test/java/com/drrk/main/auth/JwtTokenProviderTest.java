package com.drrk.main.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.drrk.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import org.junit.jupiter.api.Test;

class JwtTokenProviderTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-11T01:00:00Z"), ZoneOffset.UTC);
	private static final String SECRET = Base64.getEncoder().encodeToString("12345678901234567890123456789012".getBytes());

	@Test
	void createsAndParsesAccessTokenWithUserIdAndSessionId() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, Duration.ofMinutes(15), CLOCK);

		String token = provider.createAccessToken(1L, "session-1");
		AccessTokenClaims claims = provider.parseAccessToken(token);

		assertEquals(1L, claims.userId());
		assertEquals("session-1", claims.sessionId());
		assertEquals(Instant.parse("2026-08-11T01:15:00Z"), claims.expiresAt());
	}

	@Test
	void rejectsExpiredAccessToken() {
		JwtTokenProvider provider = new JwtTokenProvider(SECRET, Duration.ofMinutes(15), CLOCK);
		String token = provider.createAccessToken(1L, "session-1");
		JwtTokenProvider laterProvider = new JwtTokenProvider(
				SECRET,
				Duration.ofMinutes(15),
				Clock.fixed(Instant.parse("2026-08-11T01:15:01Z"), ZoneOffset.UTC)
		);

		BusinessException exception = assertThrows(BusinessException.class, () -> laterProvider.parseAccessToken(token));

		assertEquals(AuthErrorCode.INVALID_ACCESS_TOKEN, exception.getErrorCode());
	}
}
