package com.drrk.main.auth;

import java.time.Instant;

public record IssuedRefreshToken(
		String token,
		String sessionId,
		Long userId,
		Instant expiresAt
) {
}
