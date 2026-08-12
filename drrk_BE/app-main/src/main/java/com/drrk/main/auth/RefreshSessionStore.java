package com.drrk.main.auth;

import java.time.Duration;
import java.util.Optional;

public interface RefreshSessionStore {

	void save(String tokenHash, RefreshSession session, Duration ttl);

	Optional<RefreshSession> findActive(String tokenHash);

	Optional<UsedRefreshToken> findUsed(String tokenHash);

	boolean existsSession(String sessionId, Long userId);

	boolean rotate(
			String previousTokenHash,
			String nextTokenHash,
			RefreshSession nextSession,
			UsedRefreshToken usedToken,
			Duration ttl
	);

	void deleteSession(String sessionId);

	void deleteAllByUserId(Long userId);
}
