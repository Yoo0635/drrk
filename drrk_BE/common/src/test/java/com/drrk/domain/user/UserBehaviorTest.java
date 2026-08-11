package com.drrk.domain.user;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;

class UserBehaviorTest {

	@Test
	void createsActiveEmailUserWithVerifiedTimestamp() {
		LocalDateTime verifiedAt = LocalDateTime.of(2026, 8, 11, 10, 30);

		User user = User.emailUser(
				"member@example.com",
				"{bcrypt}password",
				"member",
				verifiedAt
		);

		assertEquals("member@example.com", user.getEmail());
		assertEquals("{bcrypt}password", user.getPassword());
		assertEquals(LoginType.EMAIL, user.getLoginType());
		assertNull(user.getProviderUserId());
		assertEquals("member", user.getNickname());
		assertEquals(UserStatus.ACTIVE, user.getStatus());
		assertEquals(verifiedAt, user.getEmailVerifiedAt());
		assertNotNull(user.getCreatedAt());
		assertNull(user.getDeletedAt());
	}
}
