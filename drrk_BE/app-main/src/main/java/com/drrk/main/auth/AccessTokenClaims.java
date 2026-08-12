package com.drrk.main.auth;

import java.time.Instant;

public record AccessTokenClaims(
		Long userId,
		String sessionId,
		Instant expiresAt
) {
}
