package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class LatestAirportGuideStore implements CongestionResultHandler {

	private static final Logger log = LoggerFactory.getLogger(LatestAirportGuideStore.class);
	private static final String PAYLOAD_KEY = "drrk:main:sse:airport-guide:latest";
	private static final String TIMESTAMP_KEY = "drrk:main:sse:airport-guide:latest:calculated-at";
	private static final String UPDATE_IF_LATEST_SCRIPT = """
			local current = redis.call('get', KEYS[2])
			if (not current) or (tonumber(ARGV[1]) > tonumber(current)) then
			  redis.call('set', KEYS[1], ARGV[2], 'EX', ARGV[3])
			  redis.call('set', KEYS[2], ARGV[1], 'EX', ARGV[3])
			  return 1
			end
			return 0
			""";

	private final AtomicReference<CongestionCalculatedMessage> latest = new AtomicReference<>();
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Duration redisRetention;

	public LatestAirportGuideStore() {
		this.redis = null;
		this.objectMapper = null;
		this.redisRetention = Duration.ZERO;
	}

	public LatestAirportGuideStore(
			StringRedisTemplate redis,
			ObjectMapper objectMapper,
			Duration redisRetention
	) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.redisRetention = redisRetention;
	}

	@Override
	public void handle(CongestionCalculatedMessage message) {
		if (CongestionCalculatedMessage.hasScore(message.status())) {
			if (redis != null) {
				updateRedisIfLatest(message);
				return;
			}
			CongestionCalculatedMessage stored = latest.updateAndGet(
					current -> current == null || message.calculatedAt().isAfter(current.calculatedAt())
					? message
					: current
			);
			if (stored == message) {
				log.info("[AIRPORT GUIDE UPDATED] calculatedAt={} version={} score={} trainCount={}",
						message.calculatedAt(),
						message.calculationVersion(),
						message.score(),
						message.railroadArrivals().size());
			}
		} else {
			log.info("[AIRPORT GUIDE SKIPPED] status={} calculatedAt={} reason=NO_SCORE_IN_MESSAGE",
					message.status(), message.calculatedAt());
		}
	}

	public Optional<CongestionCalculatedMessage> latest() {
		if (redis != null) {
			return latestFromRedis();
		}
		return Optional.ofNullable(latest.get());
	}

	public Optional<CongestionCalculatedMessage> latestFresh(Instant now, Duration maxAge) {
		return latest()
				.filter(message -> isFresh(message.calculatedAt(), now, maxAge));
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}

	private void updateRedisIfLatest(CongestionCalculatedMessage message) {
		Long stored = redis.execute(
				new DefaultRedisScript<>(UPDATE_IF_LATEST_SCRIPT, Long.class),
				List.of(PAYLOAD_KEY, TIMESTAMP_KEY),
				String.valueOf(message.calculatedAt().toEpochMilli()),
				toJson(message),
				String.valueOf(Math.max(1L, redisRetention.toSeconds()))
		);
		if (Long.valueOf(1L).equals(stored)) {
			log.info("[AIRPORT GUIDE UPDATED] calculatedAt={} version={} score={} trainCount={}",
					message.calculatedAt(),
					message.calculationVersion(),
					message.score(),
					message.railroadArrivals().size());
		}
	}

	private Optional<CongestionCalculatedMessage> latestFromRedis() {
		String value = redis.opsForValue().get(PAYLOAD_KEY);
		if (value == null) {
			return Optional.empty();
		}
		try {
			return Optional.of(objectMapper.readValue(value, CongestionCalculatedMessage.class));
		} catch (JacksonException exception) {
			log.warn("[AIRPORT GUIDE SKIPPED] reason=INVALID_REDIS_PAYLOAD detail={}", exception.getMessage());
			return Optional.empty();
		}
	}

	private String toJson(CongestionCalculatedMessage message) {
		try {
			return objectMapper.writeValueAsString(message);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize latest airport guide", exception);
		}
	}
}
