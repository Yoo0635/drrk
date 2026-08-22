package com.drrk.main.consumer.inference;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class LatestInferenceSnapshotStore {

	private static final Logger log = LoggerFactory.getLogger(LatestInferenceSnapshotStore.class);
	private static final String PAYLOAD_KEY = "drrk:main:sse:inference:latest";
	private static final String TIMESTAMP_KEY = "drrk:main:sse:inference:latest:window-ended-at";
	private static final String UPDATE_IF_LATEST_SCRIPT = """
			local current = redis.call('hget', KEYS[2], ARGV[1])
			if (not current) or (tonumber(ARGV[2]) > tonumber(current)) then
			  redis.call('hset', KEYS[1], ARGV[1], ARGV[3])
			  redis.call('hset', KEYS[2], ARGV[1], ARGV[2])
			  redis.call('expire', KEYS[1], ARGV[4])
			  redis.call('expire', KEYS[2], ARGV[4])
			  return 1
			end
			return 0
			""";

	private final ConcurrentMap<String, LatestInferenceSnapshot> latestBySpace = new ConcurrentHashMap<>();
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Duration redisRetention;

	public LatestInferenceSnapshotStore() {
		this.redis = null;
		this.objectMapper = null;
		this.redisRetention = Duration.ZERO;
	}

	@Autowired
	public LatestInferenceSnapshotStore(
			StringRedisTemplate redis,
			ObjectMapper objectMapper,
			@Value("${inference.stream.redis-retention:PT10M}") Duration redisRetention
	) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.redisRetention = redisRetention;
	}

	public void updateIfLatest(LatestInferenceSnapshot snapshot) {
		if (redis != null) {
			updateRedisIfLatest(snapshot);
			return;
		}
		latestBySpace.compute(snapshot.spaceId(), (spaceId, current) -> {
			if (current == null || snapshot.windowEndedAt().isAfter(current.windowEndedAt())) {
				return snapshot;
			}
			return current;
		});
	}

	public List<LatestInferenceSnapshot> findAll() {
		if (redis != null) {
			return findAllFromRedis();
		}
		return latestBySpace.values().stream()
				.sorted(Comparator.comparing(LatestInferenceSnapshot::spaceId))
				.toList();
	}

	public List<LatestInferenceSnapshot> findAllFresh(Instant now, Duration maxAge) {
		return latestBySpace.values().stream()
				.filter(snapshot -> isFresh(snapshot.windowEndedAt(), now, maxAge))
				.sorted(Comparator.comparing(LatestInferenceSnapshot::spaceId))
				.toList();
	}

	private void updateRedisIfLatest(LatestInferenceSnapshot snapshot) {
		redis.execute(
				new DefaultRedisScript<>(UPDATE_IF_LATEST_SCRIPT, Long.class),
				List.of(PAYLOAD_KEY, TIMESTAMP_KEY),
				snapshot.spaceId(),
				String.valueOf(snapshot.windowEndedAt().toEpochMilli()),
				toJson(snapshot),
				String.valueOf(Math.max(1L, redisRetention.toSeconds()))
		);
	}

	private List<LatestInferenceSnapshot> findAllFromRedis() {
		Map<Object, Object> entries = redis.opsForHash().entries(PAYLOAD_KEY);
		return entries.values().stream()
				.map(String.class::cast)
				.map(this::fromJson)
				.flatMap(List::stream)
				.sorted(Comparator.comparing(LatestInferenceSnapshot::spaceId))
				.toList();
	}

	private String toJson(LatestInferenceSnapshot snapshot) {
		try {
			return objectMapper.writeValueAsString(snapshot);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize latest inference snapshot", exception);
		}
	}

	private List<LatestInferenceSnapshot> fromJson(String value) {
		try {
			return List.of(objectMapper.readValue(value, LatestInferenceSnapshot.class));
		} catch (JacksonException exception) {
			log.warn("[INFERENCE SSE SNAPSHOT SKIPPED] reason=INVALID_REDIS_PAYLOAD detail={}", exception.getMessage());
			return List.of();
		}
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}
}
