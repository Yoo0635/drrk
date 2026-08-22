package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class LatestAirportGuideStore implements CongestionResultHandler {

	private static final Logger log = LoggerFactory.getLogger(LatestAirportGuideStore.class);
	private static final int HISTORY_LIMIT = 5;
	private static final String HISTORY_KEY = "drrk:main:sse:airport-guide:latest-history";
	private static final String LEGACY_PAYLOAD_KEY = "drrk:main:sse:airport-guide:latest";
	private static final String UPDATE_HISTORY_SCRIPT = """
			redis.call('zadd', KEYS[1], tonumber(ARGV[1]), ARGV[2])
			local total = redis.call('zcard', KEYS[1])
			if total > tonumber(ARGV[4]) then
			  redis.call('zremrangebyrank', KEYS[1], 0, total - tonumber(ARGV[4]) - 1)
			end
			redis.call('expire', KEYS[1], tonumber(ARGV[3]))
			local rank = redis.call('zrevrank', KEYS[1], ARGV[2])
			if rank and rank < tonumber(ARGV[4]) then
			  return 1
			end
			return 0
			""";

	private final AtomicReference<List<CongestionCalculatedMessage>> recent = new AtomicReference<>(List.of());
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
				updateRedisHistory(message);
				return;
			}
			List<CongestionCalculatedMessage> stored = recent.updateAndGet(current -> withLatestFive(current, message));
			if (stored.contains(message)) {
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
		return recent().stream().findFirst();
	}

	List<CongestionCalculatedMessage> recent() {
		if (redis != null) {
			return recentFromRedis();
		}
		return recent.get();
	}

	public Optional<CongestionCalculatedMessage> latestFresh(Instant now, Duration maxAge) {
		return latest()
				.filter(message -> isFresh(message.calculatedAt(), now, maxAge));
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}

	private void updateRedisHistory(CongestionCalculatedMessage message) {
		Long stored = redis.execute(
				new DefaultRedisScript<>(UPDATE_HISTORY_SCRIPT, Long.class),
				List.of(HISTORY_KEY),
				String.valueOf(message.calculatedAt().toEpochMilli()),
				toJson(message),
				String.valueOf(Math.max(1L, redisRetention.toSeconds())),
				String.valueOf(HISTORY_LIMIT)
		);
		if (Long.valueOf(1L).equals(stored)) {
			log.info("[AIRPORT GUIDE UPDATED] calculatedAt={} version={} score={} trainCount={}",
					message.calculatedAt(),
					message.calculationVersion(),
					message.score(),
					message.railroadArrivals().size());
		}
	}

	private List<CongestionCalculatedMessage> recentFromRedis() {
		Set<String> values = redis.opsForZSet().reverseRange(HISTORY_KEY, 0, HISTORY_LIMIT - 1);
		if (values != null && !values.isEmpty()) {
			return values.stream()
					.map(this::fromJson)
					.flatMap(Optional::stream)
					.toList();
		}
		return legacyLatestFromRedis()
				.map(List::of)
				.orElseGet(List::of);
	}

	private Optional<CongestionCalculatedMessage> legacyLatestFromRedis() {
		String value = redis.opsForValue().get(LEGACY_PAYLOAD_KEY);
		if (value == null) {
			return Optional.empty();
		}
		return fromJson(value);
	}

	private Optional<CongestionCalculatedMessage> fromJson(String value) {
		try {
			return Optional.of(objectMapper.readValue(value, CongestionCalculatedMessage.class));
		} catch (JacksonException exception) {
			log.warn("[AIRPORT GUIDE SKIPPED] reason=INVALID_REDIS_PAYLOAD detail={}", exception.getMessage());
			return Optional.empty();
		}
	}

	private List<CongestionCalculatedMessage> withLatestFive(
			List<CongestionCalculatedMessage> current,
			CongestionCalculatedMessage message
	) {
		List<CongestionCalculatedMessage> next = new ArrayList<>(current.size() + 1);
		next.add(message);
		next.addAll(current);
		return next.stream()
				.sorted(Comparator.comparing(CongestionCalculatedMessage::calculatedAt).reversed())
				.limit(HISTORY_LIMIT)
				.toList();
	}

	private String toJson(CongestionCalculatedMessage message) {
		try {
			return objectMapper.writeValueAsString(message);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize latest airport guide", exception);
		}
	}
}
