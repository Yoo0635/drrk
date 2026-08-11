package com.drrk.main.auth;

import java.time.Instant;

public record RefreshSession(
		String sessionId,
		Long userId,
		Instant createdAt,
		Instant lastRotatedAt
) {
}
