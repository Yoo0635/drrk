package com.drrk.main.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisRefreshSessionStore implements RefreshSessionStore {

	private static final String REFRESH_PREFIX = "auth:refresh:";
	private static final String USED_PREFIX = "auth:refresh:used:";
	private static final String SESSION_PREFIX = "auth:session:";
	private static final String USER_SESSIONS_PREFIX = "auth:user:";
	private static final String USER_SESSIONS_SUFFIX = ":sessions";

	private static final String SAVE_SCRIPT = """
			redis.call('set', KEYS[1], ARGV[1], 'EX', ARGV[4])
			redis.call('set', KEYS[2], ARGV[2], 'EX', ARGV[4])
			redis.call('sadd', KEYS[3], ARGV[3])
			redis.call('expire', KEYS[3], ARGV[4])
			""";

	private final StringRedisTemplate redis;

	public RedisRefreshSessionStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public void save(String tokenHash, RefreshSession session, Duration ttl) {
		redis.execute(
				new DefaultRedisScript<>(SAVE_SCRIPT, Void.class),
				java.util.List.of(
						refreshKey(tokenHash),
						sessionKey(session.sessionId()),
						userSessionsKey(session.userId())
				),
				encode(session),
				session.userId() + "|" + tokenHash,
				session.sessionId(),
				String.valueOf(ttl.toSeconds())
		);
	}

	@Override
	public Optional<RefreshSession> findActive(String tokenHash) {
		return Optional.ofNullable(redis.opsForValue().get(refreshKey(tokenHash)))
				.map(this::decodeSession);
	}

	@Override
	public Optional<UsedRefreshToken> findUsed(String tokenHash) {
		return Optional.ofNullable(redis.opsForValue().get(usedKey(tokenHash)))
				.map(this::decodeUsed);
	}

	@Override
	public boolean existsSession(String sessionId, Long userId) {
		String value = redis.opsForValue().get(sessionKey(sessionId));
		return value != null && value.startsWith(userId + "|");
	}

	@Override
	public boolean rotate(
			String previousTokenHash,
			String nextTokenHash,
			RefreshSession nextSession,
			UsedRefreshToken usedToken,
			Duration ttl
	) {
		String script = """
				if redis.call('exists', KEYS[1]) == 0 then
				  return 0
				end
				redis.call('del', KEYS[1])
				redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[5])
				redis.call('set', KEYS[3], ARGV[2], 'EX', ARGV[5])
				redis.call('set', KEYS[4], ARGV[3], 'EX', ARGV[5])
				redis.call('sadd', KEYS[5], ARGV[4])
				redis.call('expire', KEYS[5], ARGV[5])
				return 1
				""";
		Long result = redis.execute(
				new DefaultRedisScript<>(script, Long.class),
				java.util.List.of(
						refreshKey(previousTokenHash),
						usedKey(previousTokenHash),
						refreshKey(nextTokenHash),
						sessionKey(nextSession.sessionId()),
						userSessionsKey(nextSession.userId())
				),
				encode(usedToken),
				encode(nextSession),
				nextSession.userId() + "|" + nextTokenHash,
				nextSession.sessionId(),
				String.valueOf(ttl.toSeconds())
		);
		return Long.valueOf(1L).equals(result);
	}

	@Override
	public void deleteSession(String sessionId) {
		String sessionValue = redis.opsForValue().get(sessionKey(sessionId));
		if (sessionValue == null) {
			return;
		}
		String[] parts = sessionValue.split("\\|", 2);
		Long userId = Long.valueOf(parts[0]);
		String tokenHash = parts[1];
		redis.delete(refreshKey(tokenHash));
		redis.delete(sessionKey(sessionId));
		redis.opsForSet().remove(userSessionsKey(userId), sessionId);
	}

	@Override
	public void deleteAllByUserId(Long userId) {
		Set<String> sessionIds = redis.opsForSet().members(userSessionsKey(userId));
		if (sessionIds != null) {
			for (String sessionId : sessionIds) {
				deleteSession(sessionId);
			}
		}
		redis.delete(userSessionsKey(userId));
	}

	private String refreshKey(String tokenHash) {
		return REFRESH_PREFIX + tokenHash;
	}

	private String usedKey(String tokenHash) {
		return USED_PREFIX + tokenHash;
	}

	private String sessionKey(String sessionId) {
		return SESSION_PREFIX + sessionId;
	}

	private String userSessionsKey(Long userId) {
		return USER_SESSIONS_PREFIX + userId + USER_SESSIONS_SUFFIX;
	}

	private String encode(RefreshSession session) {
		return session.sessionId() + "|" + session.userId() + "|" + session.createdAt() + "|" + session.lastRotatedAt();
	}

	private String encode(UsedRefreshToken usedToken) {
		return usedToken.sessionId() + "|" + usedToken.userId() + "|" + usedToken.usedAt() + "|" + usedToken.replacementTokenHash();
	}

	private RefreshSession decodeSession(String value) {
		String[] parts = value.split("\\|", 4);
		return new RefreshSession(parts[0], Long.valueOf(parts[1]), Instant.parse(parts[2]), Instant.parse(parts[3]));
	}

	private UsedRefreshToken decodeUsed(String value) {
		String[] parts = value.split("\\|", 4);
		return new UsedRefreshToken(parts[0], Long.valueOf(parts[1]), Instant.parse(parts[2]), parts[3]);
	}
}
