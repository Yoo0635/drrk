package com.drrk.main.consumer.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

class LatestAirportGuideStoreTest {

	@Test
	void keepsOnlyTheNewestOneHundredTwentyCalculatedResultsAndIgnoresPendingMessages() {
		Instant base = Instant.parse("2026-08-13T05:01:00Z");
		LatestAirportGuideStore store = storeAt(base.plusSeconds(600));
		List<CongestionCalculatedMessage> messages = java.util.stream.IntStream.rangeClosed(0, 120)
				.mapToObj(offset -> calculatedAt(base.plusSeconds(offset)))
				.toList();

		messages.forEach(store::handle);
		store.handle(calculatedAt(base.minusSeconds(1)));
		store.handle(CongestionCalculatedMessage.formulaPending(
				UUID.randomUUID(),
				base.plusSeconds(6),
				inputs()
		));

		assertTrue(store.latest().isPresent());
		assertEquals(messages.get(120).messageId(), store.latest().orElseThrow().messageId());
		assertEquals(120, store.recent().size());
		assertTrue(store.recent().stream().noneMatch(message -> message.messageId().equals(messages.get(0).messageId())));
		assertIterableEquals(
				List.of(messages.get(120).messageId(), messages.get(119).messageId(), messages.get(118).messageId()),
				store.recent().stream()
						.map(CongestionCalculatedMessage::messageId)
						.limit(3)
						.toList()
		);
	}

	@Test
	void ignoresNoServiceMessagesButStoresNoFlightDataResults() {
		Instant base = Instant.parse("2026-08-13T05:01:00Z");
		LatestAirportGuideStore store = storeAt(base.plusSeconds(1));

		store.handle(CongestionCalculatedMessage.noService(UUID.randomUUID(), base, inputs()));
		assertTrue(store.latest().isEmpty());

		CongestionCalculatedMessage measuredOnly = CongestionCalculatedMessage.noFlightData(
				UUID.randomUUID(),
				base.plusSeconds(1),
				"platform-congestion-v2",
				true,
				12.0,
				48L,
				0.0,
				base.minusSeconds(300),
				List.of(),
				inputs()
		);
		store.handle(measuredOnly);

		assertEquals(measuredOnly.messageId(), store.latest().orElseThrow().messageId());
	}

	@Test
	void expiresEntriesByCalculatedAtAfterTenMinutes() {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		LatestAirportGuideStore store = storeAt(now);

		store.handle(calculatedAt(now.minusSeconds(600)));
		CongestionCalculatedMessage fresh = calculatedAt(now.minusSeconds(599));
		store.handle(fresh);

		assertEquals(List.of(fresh.messageId()), store.recent().stream()
				.map(CongestionCalculatedMessage::messageId)
				.toList());
	}

	@Test
	void waitsForWriteLockBeforeReadingLatest() throws Exception {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		LatestAirportGuideStore store = storeAt(now);
		CongestionCalculatedMessage latest = calculatedAt(now.minusSeconds(1));
		store.handle(latest);
		ReentrantReadWriteLock historyLock = (ReentrantReadWriteLock) ReflectionTestUtils.getField(store, "historyLock");
		assertNotNull(historyLock);
		ExecutorService executor = Executors.newSingleThreadExecutor();
		historyLock.writeLock().lock();
		Future<Optional<CongestionCalculatedMessage>> reader = executor.submit(() ->
				store.latestFresh(now, Duration.ofMinutes(10)));

		try {
			assertThrows(TimeoutException.class, () -> reader.get(100, TimeUnit.MILLISECONDS));
		} finally {
			historyLock.writeLock().unlock();
		}

		try {
			assertEquals(latest.messageId(), reader.get(1, TimeUnit.SECONDS).orElseThrow().messageId());
		} finally {
			executor.shutdownNow();
		}
	}

	@Test
	void doesNotUpdateLocalCacheWhenRedisUpdateFails() {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		LatestAirportGuideStore store = new LatestAirportGuideStore(
				new FailingRedisTemplate(),
				new ObjectMapper(),
				Duration.ofMinutes(10),
				Clock.fixed(now, ZoneOffset.UTC)
		);

		assertThrows(IllegalStateException.class, () -> store.handle(calculatedAt(now.minusSeconds(1))));

		assertTrue(store.latest().isEmpty());
		assertTrue(store.recent().isEmpty());
	}

	private CongestionCalculatedMessage calculatedAt(Instant calculatedAt) {
		return CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				calculatedAt,
				"platform-congestion-v2",
				false,
				12.0,
				48L,
				6.0,
				calculatedAt.minusSeconds(300),
				List.of(),
				inputs()
		);
	}

	private CongestionInputReferences inputs() {
		return new CongestionInputReferences(
				Instant.EPOCH,
				0,
				Instant.EPOCH,
				0,
				Instant.EPOCH,
				0,
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				Instant.EPOCH
		);
	}

	private LatestAirportGuideStore storeAt(Instant now) {
		return new LatestAirportGuideStore(
				null,
				null,
				Duration.ofMinutes(10),
				Clock.fixed(now, ZoneOffset.UTC)
		);
	}

	private static final class FailingRedisTemplate extends StringRedisTemplate {

		@Override
		public <T> T execute(RedisScript<T> script, List<String> keys, Object... args) {
			throw new IllegalStateException("redis unavailable");
		}
	}
}
