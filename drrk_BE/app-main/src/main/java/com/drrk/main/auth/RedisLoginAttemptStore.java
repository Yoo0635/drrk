package com.drrk.main.auth;

import java.time.Duration;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisLoginAttemptStore implements LoginAttemptStore {

	private static final String PREFIX = "auth:login:failures:";

	private final StringRedisTemplate redis;

	public RedisLoginAttemptStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public boolean isLocked(String email) {
		String value = redis.opsForValue().get(PREFIX + email);
		return value != null && Integer.parseInt(value) >= 5;
	}

	@Override
	public int recordFailure(String email, Duration ttl) {
		String key = PREFIX + email;
		Long failures = redis.opsForValue().increment(key);
		redis.expire(key, ttl);
		return failures == null ? 1 : failures.intValue();
	}

	@Override
	public void clear(String email) {
		redis.delete(PREFIX + email);
	}
}
