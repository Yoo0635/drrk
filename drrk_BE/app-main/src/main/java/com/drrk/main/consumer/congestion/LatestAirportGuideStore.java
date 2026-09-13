package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Scheduled;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

public class LatestAirportGuideStore {

	private static final Logger log = LoggerFactory.getLogger(LatestAirportGuideStore.class);
	static final int HISTORY_LIMIT = 120;
	private static final String HISTORY_KEY = "drrk:main:sse:airport-guide:v2:history";
	private static final String PAYLOAD_KEY = "drrk:main:sse:airport-guide:v2:payloads";
	private static final String UPDATE_HISTORY_SCRIPT = """
			redis.call('zadd', KEYS[1], tonumber(ARGV[1]), ARGV[2])
			redis.call('hset', KEYS[2], ARGV[2], ARGV[3])

			local expired = redis.call('zrangebyscore', KEYS[1], '-inf', tonumber(ARGV[4]))
			if #expired > 0 then
			  redis.call('zrem', KEYS[1], unpack(expired))
			  redis.call('hdel', KEYS[2], unpack(expired))
			end

			local total = redis.call('zcard', KEYS[1])
			if total > tonumber(ARGV[6]) then
			  local overflow = redis.call('zrange', KEYS[1], 0, total - tonumber(ARGV[6]) - 1)
			  if #overflow > 0 then
			    redis.call('zrem', KEYS[1], unpack(overflow))
			    redis.call('hdel', KEYS[2], unpack(overflow))
			  end
			end

			redis.call('expire', KEYS[1], tonumber(ARGV[5]))
			redis.call('expire', KEYS[2], tonumber(ARGV[5]))
			return 1
			""";

	private final HashMap<String, CongestionSnapshot> recent = new HashMap<>();
	private final ReentrantReadWriteLock historyLock = new ReentrantReadWriteLock();
	private final StringRedisTemplate redis;
	private final ObjectMapper objectMapper;
	private final Duration retention;
	private final Clock clock;

	public LatestAirportGuideStore() {
		this(null, null, Duration.ofMinutes(10), Clock.systemUTC());
	}

	public LatestAirportGuideStore(
			StringRedisTemplate redis,
			ObjectMapper objectMapper,
			Duration retention,
			Clock clock
	) {
		this.redis = redis;
		this.objectMapper = objectMapper;
		this.retention = retention;
		this.clock = clock;
	}

	public Optional<CongestionSnapshot> handle(CongestionCalculatedMessage message) {
		return handle(message, CongestionDeliveryStatus.LIVE, 0);
	}

	public Optional<CongestionSnapshot> handle(
			CongestionCalculatedMessage message,
			CongestionDeliveryStatus deliveryStatus,
			int retryCount
	) {
		if (!CongestionCalculatedMessage.hasScore(message.status())) {
			log.info("[AIRPORT GUIDE SKIPPED] status={} calculatedAt={} reason=NO_SCORE_IN_MESSAGE",
					message.status(), message.calculatedAt());
			return Optional.empty();
		}

		Instant now = now();
		if (!isInWindow(message.calculatedAt(), now, retention)) {
			log.info("[AIRPORT GUIDE SKIPPED] messageId={} calculatedAt={} reason=OUT_OF_LOCAL_WINDOW",
					message.messageId(), message.calculatedAt());
			return Optional.empty();
		}

		CongestionSnapshot snapshot = new CongestionSnapshot(message, deliveryStatus, retryCount, now);
		updateRedisHistory(snapshot, now);
		historyLock.writeLock().lock();
		try {
			CongestionSnapshot merged = merge(recent.get(message.messageId()), snapshot);
			recent.put(message.messageId(), merged);
			pruneLocked(now);
		} finally {
			historyLock.writeLock().unlock();
		}
		log.info("[AIRPORT GUIDE UPDATED] calculatedAt={} version={} score={} trainCount={}",
				message.calculatedAt(),
				message.calculationVersion(),
				message.score(),
				message.railroadArrivals().size());
		return Optional.of(snapshot);
	}

	public Optional<CongestionCalculatedMessage> latest() {
		return latestFresh(now(), retention);
	}

	List<CongestionCalculatedMessage> recent() {
		return recentSnapshots(now()).stream()
				.map(CongestionSnapshot::message)
				.sorted(messageDescending())
				.toList();
	}

	public List<CongestionSnapshot> recentSnapshots(Instant now) {
		List<CongestionSnapshot> snapshots;
		historyLock.readLock().lock();
		try {
			snapshots = List.copyOf(recent.values());
		} finally {
			historyLock.readLock().unlock();
		}
		return snapshots.stream()
				.filter(snapshot -> isInWindow(snapshot.message().calculatedAt(), now, retention))
				.sorted(snapshotAscending())
				.toList();
	}

	public Optional<CongestionCalculatedMessage> latestFresh(Instant now, Duration maxAge) {
		historyLock.readLock().lock();
		try {
			return recent.values().stream()
					.filter(snapshot -> isInWindow(snapshot.message().calculatedAt(), now, maxAge))
					.max(snapshotAscending())
					.map(CongestionSnapshot::message);
		} finally {
			historyLock.readLock().unlock();
		}
	}

	@Scheduled(fixedRateString = "${congestion.cache.cleanup-fixed-rate:PT1S}")
	void cleanupExpired() {
		historyLock.writeLock().lock();
		try {
			pruneLocked(now());
		} finally {
			historyLock.writeLock().unlock();
		}
	}

	private CongestionSnapshot merge(CongestionSnapshot current, CongestionSnapshot incoming) {
		if (current == null || current.deliveryStatus() != CongestionDeliveryStatus.RECOVERED_LATE) {
			return incoming;
		}
		if (incoming.deliveryStatus() == CongestionDeliveryStatus.RECOVERED_LATE) {
			return incoming;
		}
		return current;
	}

	private void updateRedisHistory(CongestionSnapshot snapshot, Instant now) {
		if (redis == null) {
			return;
		}
		long ttlSeconds = Math.max(1L, retention.toSeconds());
		redis.execute(
				new DefaultRedisScript<>(UPDATE_HISTORY_SCRIPT, Long.class),
				List.of(HISTORY_KEY, PAYLOAD_KEY),
				String.valueOf(snapshot.message().calculatedAt().toEpochMilli()),
				snapshot.message().messageId(),
				toJson(snapshot),
				String.valueOf(now.minus(retention).toEpochMilli()),
				String.valueOf(ttlSeconds),
				String.valueOf(HISTORY_LIMIT)
		);
	}

	private void pruneLocked(Instant now) {
		Instant cutoff = now.minus(retention);
		recent.entrySet().removeIf(entry -> !entry.getValue().message().calculatedAt().isAfter(cutoff)
				|| entry.getValue().message().calculatedAt().isAfter(now));
		int overflow = recent.size() - HISTORY_LIMIT;
		if (overflow <= 0) {
			return;
		}
		recent.values().stream()
				.sorted(snapshotAscending())
				.limit(overflow)
				.map(snapshot -> snapshot.message().messageId())
				.toList()
				.forEach(recent::remove);
	}

	private static boolean isInWindow(Instant timestamp, Instant now, Duration retention) {
		return !timestamp.isAfter(now) && timestamp.isAfter(now.minus(retention));
	}

	private static Comparator<CongestionCalculatedMessage> messageAscending() {
		return Comparator.comparing(CongestionCalculatedMessage::calculatedAt)
				.thenComparing(CongestionCalculatedMessage::messageId);
	}

	private static Comparator<CongestionCalculatedMessage> messageDescending() {
		return messageAscending().reversed();
	}

	private static Comparator<CongestionSnapshot> snapshotAscending() {
		return Comparator.comparing((CongestionSnapshot snapshot) -> snapshot.message().calculatedAt())
				.thenComparing(snapshot -> snapshot.message().messageId());
	}

	private String toJson(CongestionSnapshot snapshot) {
		try {
			return objectMapper.writeValueAsString(snapshot);
		} catch (JacksonException exception) {
			throw new IllegalStateException("failed to serialize latest airport guide", exception);
		}
	}

	private Instant now() {
		return clock.instant();
	}
}
