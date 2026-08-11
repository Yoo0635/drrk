package com.drrk.main.auth;

import java.time.Instant;

public record RotatedRefreshToken(
		String token,
		String sessionId,
		Long userId,
		Instant expiresAt
) {
}
