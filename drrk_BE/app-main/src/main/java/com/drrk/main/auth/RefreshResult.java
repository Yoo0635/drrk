package com.drrk.main.auth;

public record RefreshResult(
		String accessToken,
		String refreshToken
) {
}
