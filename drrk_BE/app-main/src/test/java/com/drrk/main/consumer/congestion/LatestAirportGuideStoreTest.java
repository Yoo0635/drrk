package com.drrk.main.consumer.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LatestAirportGuideStoreTest {

	@Test
	void keepsOnlyTheNewestCalculatedResultAndIgnoresPendingMessages() {
		LatestAirportGuideStore store = new LatestAirportGuideStore();
		Instant newerTime = Instant.parse("2026-08-13T05:01:00Z");
		CongestionCalculatedMessage newer = calculatedAt(newerTime);

		store.handle(newer);
		store.handle(calculatedAt(newerTime.minusSeconds(1)));
		store.handle(CongestionCalculatedMessage.formulaPending(
				UUID.randomUUID(),
				newerTime.plusSeconds(1),
				inputs()
		));

		assertTrue(store.latest().isPresent());
		assertEquals(newer.messageId(), store.latest().orElseThrow().messageId());
	}

	@Test
	void ignoresNoServiceMessagesButStoresNoFlightDataResults() {
		LatestAirportGuideStore store = new LatestAirportGuideStore();
		Instant base = Instant.parse("2026-08-13T05:01:00Z");

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
}
