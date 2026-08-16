package com.drrk.main.auth;

import com.drrk.global.error.BusinessException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

public class RefreshTokenService {

	private final RefreshSessionStore store;
	private final TokenGenerator tokenGenerator;
	private final Sha256TokenHasher tokenHasher;
	private final Clock clock;
	private final Duration refreshTtl;

	public RefreshTokenService(
			RefreshSessionStore store,
			TokenGenerator tokenGenerator,
			Sha256TokenHasher tokenHasher,
			Clock clock,
			Duration refreshTtl
	) {
		this.store = store;
		this.tokenGenerator = tokenGenerator;
		this.tokenHasher = tokenHasher;
		this.clock = clock;
		this.refreshTtl = refreshTtl;
	}

	public IssuedRefreshToken issue(Long userId) {
		Instant now = clock.instant();
		String token = tokenGenerator.generate();
		String sessionId = UUID.randomUUID().toString();
		RefreshSession session = new RefreshSession(sessionId, userId, now, now);

		store.save(tokenHasher.hash(token), session, refreshTtl);

		return new IssuedRefreshToken(token, sessionId, userId, now.plus(refreshTtl));
	}

	public RotatedRefreshToken rotate(String refreshToken) {
		String previousTokenHash = tokenHasher.hash(refreshToken);
		Instant now = clock.instant();

		return store.findActive(previousTokenHash)
				.map(session -> rotateActiveToken(previousTokenHash, session, now))
				.orElseGet(() -> handleMissingActiveToken(previousTokenHash));
	}

	public void deleteSession(String sessionId) {
		store.deleteSession(sessionId);
	}

	public void deleteByRefreshToken(String refreshToken) {
		String tokenHash = tokenHasher.hash(refreshToken);
		store.findActive(tokenHash).ifPresent(session -> store.deleteSession(session.sessionId()));
	}

	private RotatedRefreshToken rotateActiveToken(String previousTokenHash, RefreshSession session, Instant now) {
		String nextToken = tokenGenerator.generate();
		String nextTokenHash = tokenHasher.hash(nextToken);
		RefreshSession nextSession = new RefreshSession(
				session.sessionId(),
				session.userId(),
				session.createdAt(),
				now
		);
		UsedRefreshToken usedToken = new UsedRefreshToken(session.sessionId(), session.userId(), now, nextTokenHash);

		boolean rotated = store.rotate(previousTokenHash, nextTokenHash, nextSession, usedToken, refreshTtl);
		if (!rotated) {
			return handleMissingActiveToken(previousTokenHash);
		}

		return new RotatedRefreshToken(nextToken, session.sessionId(), session.userId(), now.plus(refreshTtl));
	}

	private RotatedRefreshToken handleMissingActiveToken(String previousTokenHash) {
		return store.findUsed(previousTokenHash)
				.map(this::handleReusedToken)
				.orElseThrow(() -> new BusinessException(AuthErrorCode.INVALID_REFRESH_TOKEN));
	}

	private RotatedRefreshToken handleReusedToken(UsedRefreshToken usedToken) {
		store.deleteAllByUserId(usedToken.userId());
		throw new BusinessException(AuthErrorCode.REFRESH_TOKEN_REUSED);
	}
}
