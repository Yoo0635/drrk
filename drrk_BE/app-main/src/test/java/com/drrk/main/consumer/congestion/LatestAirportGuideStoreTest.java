package com.drrk.main.consumer.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
import com.drrk.messaging.congestion.RouteStatus;
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

	private CongestionCalculatedMessage calculatedAt(Instant calculatedAt) {
		return CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				calculatedAt,
				"moving-walkway-v1",
				false,
				List.of(
						route(AirportRoute.A, calculatedAt, 80),
						route(AirportRoute.B, calculatedAt, 50),
						route(AirportRoute.C, calculatedAt, 90)
				),
				AirportRoute.B,
				List.of(),
				inputs()
		);
	}

	private RouteCongestionResult route(AirportRoute airportRoute, Instant calculatedAt, long totalTravelTimeSeconds) {
		return new RouteCongestionResult(
				airportRoute,
				calculatedAt.plusSeconds(20),
				1,
				1,
				1,
				3,
				3 / 4.2,
				MovingWalkwayStatus.NORMAL,
				RouteStatus.CLEAR,
				10,
				totalTravelTimeSeconds
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
