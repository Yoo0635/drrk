package com.drrk.main.auth;

public record CurrentUserResponse(
		Long id,
		String email,
		String nickname
) {
}
