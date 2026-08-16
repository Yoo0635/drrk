package com.drrk.main.auth;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RefreshTokenServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-11T01:00:00Z"), ZoneOffset.UTC);

	@Test
	void rotatesRefreshTokenAndMarksPreviousTokenAsUsed() {
		FakeRefreshSessionStore store = new FakeRefreshSessionStore();
		RefreshTokenService service = new RefreshTokenService(
				store,
				new FixedTokenGenerator("old-token", "new-token"),
				new Sha256TokenHasher(),
				CLOCK,
				Duration.ofDays(14)
		);
		IssuedRefreshToken issued = service.issue(1L);

		RotatedRefreshToken rotated = service.rotate(issued.token());

		assertEquals(issued.sessionId(), rotated.sessionId());
		assertNotEquals(issued.token(), rotated.token());
		assertTrue(store.usedTokenHashes.contains(new Sha256TokenHasher().hash("old-token")));
		assertTrue(store.activeTokenHashes.contains(new Sha256TokenHasher().hash("new-token")));
	}

	@Test
	void deletesAllUserSessionsWhenUsedRefreshTokenIsReused() {
		FakeRefreshSessionStore store = new FakeRefreshSessionStore();
		RefreshTokenService service = new RefreshTokenService(
				store,
				new FixedTokenGenerator("old-token", "new-token"),
				new Sha256TokenHasher(),
				CLOCK,
				Duration.ofDays(14)
		);
		IssuedRefreshToken issued = service.issue(1L);
		service.rotate(issued.token());

		BusinessException exception = assertThrows(BusinessException.class, () -> service.rotate(issued.token()));

		assertEquals(AuthErrorCode.REFRESH_TOKEN_REUSED, exception.getErrorCode());
		assertTrue(store.deletedAllUsers.contains(1L));
		assertTrue(store.activeTokenHashes.isEmpty());
	}

	private static final class FakeRefreshSessionStore implements RefreshSessionStore {

		private final Map<String, RefreshSession> active = new HashMap<>();
		private final Map<String, UsedRefreshToken> used = new HashMap<>();
		private final Set<String> activeTokenHashes = new HashSet<>();
		private final Set<String> usedTokenHashes = new HashSet<>();
		private final Set<Long> deletedAllUsers = new HashSet<>();

		@Override
		public void save(String tokenHash, RefreshSession session, Duration ttl) {
			active.put(tokenHash, session);
			activeTokenHashes.add(tokenHash);
		}

		@Override
		public Optional<RefreshSession> findActive(String tokenHash) {
			return Optional.ofNullable(active.get(tokenHash));
		}

		@Override
		public Optional<UsedRefreshToken> findUsed(String tokenHash) {
			return Optional.ofNullable(used.get(tokenHash));
		}

		@Override
		public boolean existsSession(String sessionId, Long userId) {
			return active.values().stream()
					.anyMatch(session -> session.sessionId().equals(sessionId) && session.userId().equals(userId));
		}

		@Override
		public boolean rotate(
				String previousTokenHash,
				String nextTokenHash,
				RefreshSession nextSession,
				UsedRefreshToken usedToken,
				Duration ttl
		) {
			if (!active.containsKey(previousTokenHash)) {
				return false;
			}
			active.remove(previousTokenHash);
			activeTokenHashes.remove(previousTokenHash);
			used.put(previousTokenHash, usedToken);
			usedTokenHashes.add(previousTokenHash);
			active.put(nextTokenHash, nextSession);
			activeTokenHashes.add(nextTokenHash);
			return true;
		}

		@Override
		public void deleteSession(String sessionId) {
			active.entrySet().removeIf(entry -> entry.getValue().sessionId().equals(sessionId));
			activeTokenHashes.removeIf(tokenHash -> !active.containsKey(tokenHash));
		}

		@Override
		public void deleteAllByUserId(Long userId) {
			deletedAllUsers.add(userId);
			active.entrySet().removeIf(entry -> entry.getValue().userId().equals(userId));
			activeTokenHashes.removeIf(tokenHash -> !active.containsKey(tokenHash));
		}
	}

	private static final class FixedTokenGenerator implements TokenGenerator {

		private final java.util.Queue<String> tokens;

		private FixedTokenGenerator(String... tokens) {
			this.tokens = new java.util.ArrayDeque<>(java.util.Arrays.asList(tokens));
		}

		@Override
		public String generate() {
			return tokens.remove();
		}
	}
}
