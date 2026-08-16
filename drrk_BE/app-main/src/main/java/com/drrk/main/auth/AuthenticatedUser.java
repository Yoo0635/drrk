package com.drrk.main.auth;

public record AuthenticatedUser(
		Long userId,
		String sessionId
) {
}
