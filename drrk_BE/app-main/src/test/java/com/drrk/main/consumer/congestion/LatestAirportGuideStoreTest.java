package com.drrk.main.consumer.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.anyList;
import static org.mockito.Mockito.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

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
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.RedisConnectionFailureException;
import org.springframework.data.redis.core.script.RedisScript;
import tools.jackson.databind.ObjectMapper;

class LatestAirportGuideStoreTest {

	@Test
	void restoresHistoryWhenSpringInitializesTheStore() {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		ObjectMapper mapper = new ObjectMapper();
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		CongestionSnapshot snapshot = new CongestionSnapshot(calculatedAt(now.minusSeconds(5)),
				CongestionDeliveryStatus.LIVE, 0, now);
		when(hashes.values(anyString())).thenReturn(List.of(mapper.writeValueAsString(snapshot)));
		try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
			context.registerBean(LatestAirportGuideStore.class, () -> new LatestAirportGuideStore(
					redis, mapper, Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC)));
			context.refresh();
			verify(hashes).values("drrk:main:sse:airport-guide:v2:payloads");
			assertEquals(List.of(snapshot), context.getBean(LatestAirportGuideStore.class).recentSnapshots(now));
			verify(hashes, times(1)).values(anyString());
		}
	}

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
				failingRedis(),
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

	private StringRedisTemplate failingRedis() {
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		when(hashes.values(anyString())).thenReturn(List.of());
		when(redis.execute(any(RedisScript.class), anyList(), any(Object[].class)))
				.thenThrow(new IllegalStateException("redis unavailable"));
		return redis;
	}

	@Test
	void restoresRedisHistoryForBothReadPathsAndThenUsesLocalCache() {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		ObjectMapper mapper = new ObjectMapper();
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		CongestionSnapshot snapshot = new CongestionSnapshot(calculatedAt(now.minusSeconds(5)),
				CongestionDeliveryStatus.RECOVERED_LATE, 2, now);
		when(hashes.values("drrk:main:sse:airport-guide:v2:payloads"))
				.thenReturn(List.of(mapper.writeValueAsString(snapshot)));
		LatestAirportGuideStore store = new LatestAirportGuideStore(redis, mapper,
				Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC));

		assertEquals(snapshot.message(), store.latestFresh(now, Duration.ofSeconds(25)).orElseThrow());
		assertEquals(List.of(snapshot), store.recentSnapshots(now));
		verify(hashes, times(1)).values(anyString());
		verify(redis, never()).execute(any(RedisScript.class), anyList(), any(Object[].class));
	}

	@Test
	void restoresOnlyValidRecentHistoryAndEnforcesLimit() {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		ObjectMapper mapper = new ObjectMapper();
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		List<Object> payloads = new java.util.ArrayList<>();
		for (int age = 0; age <= 120; age++) {
			payloads.add(mapper.writeValueAsString(new CongestionSnapshot(calculatedAt(now.minusSeconds(age)),
					CongestionDeliveryStatus.LIVE, 0, now)));
		}
		for (Instant timestamp : List.of(now.minusSeconds(600), now.plusSeconds(1))) {
			payloads.add(mapper.writeValueAsString(new CongestionSnapshot(calculatedAt(timestamp),
					CongestionDeliveryStatus.LIVE, 0, now)));
		}
		payloads.add("broken json");
		payloads.add("null");
		payloads.add("{}");
		when(hashes.values(anyString())).thenReturn(payloads);
		LatestAirportGuideStore store = new LatestAirportGuideStore(redis, mapper,
				Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC));

		List<CongestionSnapshot> restored = store.recentSnapshots(now);
		assertEquals(120, restored.size());
		assertEquals(now.minusSeconds(119), restored.getFirst().message().calculatedAt());
		assertEquals(now, restored.getLast().message().calculatedAt());
	}

	@Test
	void retriesFailedRedisRecoveryAndPreservesConcurrentLocalUpdate() {
		Instant now = Instant.parse("2026-08-13T05:11:00Z");
		ObjectMapper mapper = new ObjectMapper();
		StringRedisTemplate redis = mock(StringRedisTemplate.class);
		HashOperations<String, Object, Object> hashes = mock(HashOperations.class);
		when(redis.opsForHash()).thenReturn(hashes);
		LatestAirportGuideStore store = new LatestAirportGuideStore(redis, mapper,
				Duration.ofMinutes(10), Clock.fixed(now, ZoneOffset.UTC));
		CongestionCalculatedMessage message = calculatedAt(now.minusSeconds(5));
		when(hashes.values(anyString()))
				.thenThrow(new RedisConnectionFailureException("unavailable"))
				.thenAnswer(invocation -> {
					store.handle(message, CongestionDeliveryStatus.RECOVERED_LATE, 2);
					return List.of(mapper.writeValueAsString(new CongestionSnapshot(message,
							CongestionDeliveryStatus.LIVE, 0, now)));
				});

		assertTrue(store.recentSnapshots(now).isEmpty());
		assertEquals(CongestionDeliveryStatus.RECOVERED_LATE,
				store.recentSnapshots(now).getFirst().deliveryStatus());
		verify(hashes, times(2)).values(anyString());
	}
}
