package com.drrk.main.auth;

public record LoginResult(
		String accessToken,
		String refreshToken,
		String refreshSessionId
) {
}
