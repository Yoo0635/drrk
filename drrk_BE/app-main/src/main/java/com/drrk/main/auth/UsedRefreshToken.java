package com.drrk.main.auth;

import java.time.Instant;

public record UsedRefreshToken(
		String sessionId,
		Long userId,
		Instant usedAt,
		String replacementTokenHash
) {
}
