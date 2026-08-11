package com.drrk.main.auth;

import java.time.Duration;
import java.util.Optional;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class RedisEmailVerificationStore implements EmailVerificationStore {

	private static final String CODE_PREFIX = "auth:email:code:";
	private static final String TICKET_PREFIX = "auth:email:ticket:";

	private final StringRedisTemplate redis;

	public RedisEmailVerificationStore(StringRedisTemplate redis) {
		this.redis = redis;
	}

	@Override
	public void saveCode(String email, String codeHash, Duration ttl) {
		redis.opsForValue().set(CODE_PREFIX + email, codeHash, ttl);
	}

	@Override
	public Optional<String> findCodeHash(String email) {
		return Optional.ofNullable(redis.opsForValue().get(CODE_PREFIX + email));
	}

	@Override
	public void deleteCode(String email) {
		redis.delete(CODE_PREFIX + email);
	}

	@Override
	public void saveSignupTicket(String ticket, String email, Duration ttl) {
		redis.opsForValue().set(TICKET_PREFIX + ticket, email, ttl);
	}

	@Override
	public Optional<String> consumeSignupTicket(String ticket) {
		String key = TICKET_PREFIX + ticket;
		String email = redis.opsForValue().get(key);
		if (email != null) {
			redis.delete(key);
		}
		return Optional.ofNullable(email);
	}
}
