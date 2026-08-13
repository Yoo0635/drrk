package com.drrk.messaging.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CongestionMessageContractTest {

	@Test
	void createsFormulaPendingMessageWithTraceableInputSnapshots() {
		CongestionInputReferences inputs = new CongestionInputReferences(
				Instant.parse("2026-08-13T00:00:00Z"),
				2,
				Instant.parse("2026-08-13T00:00:01Z"),
				3,
				Instant.parse("2026-08-13T00:00:02Z"),
				4,
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				Instant.parse("2026-08-13T00:00:03Z")
		);

		CongestionCalculatedMessage message = CongestionCalculatedMessage.formulaPending(
				UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816"),
				Instant.parse("2026-08-13T00:00:10Z"),
				inputs
		);

		assertEquals("35c9ef91-9f68-4fda-833f-90fa54c25816", message.messageId());
		assertEquals("2.0", message.schemaVersion());
		assertEquals("formula-pending-v0", message.calculationVersion());
		assertEquals(CongestionCalculationStatus.FORMULA_PENDING, message.status());
		assertNull(message.score());
		assertNull(message.level());
		assertEquals(inputs, message.inputs());
	}

	@Test
	void rejectsNegativeSourceItemCounts() {
		assertThrows(
				IllegalArgumentException.class,
				() -> new CongestionInputReferences(
						Instant.EPOCH,
						-1,
						Instant.EPOCH,
						0,
						Instant.EPOCH,
						0,
						"model-message-id",
						Instant.EPOCH
				)
		);
	}

	@Test
	void createsCalculatedGuideWithTwoRoutesAndRailroadArrivals() {
		CongestionInputReferences inputs = inputs();
		RouteCongestionResult routeB = new RouteCongestionResult(
				AirportRoute.B,
				Instant.parse("2026-08-13T05:00:30Z"),
				1.0,
				2.0,
				0.5,
				3.5,
				3.5 / 4.2,
				MovingWalkwayStatus.AVAILABLE,
				20,
				80
		);
		RouteCongestionResult routeC = new RouteCongestionResult(
				AirportRoute.C,
				Instant.parse("2026-08-13T05:00:40Z"),
				3.0,
				2.0,
				1.0,
				6.0,
				6.0 / 4.2,
				MovingWalkwayStatus.CONGESTED,
				50,
				110
		);
		RailroadArrivalResult train = new RailroadArrivalResult(
				"1234",
				"일반",
				"14:55",
				null,
				RailroadArrivalStatus.SCHEDULED
		);

		CongestionCalculatedMessage message = CongestionCalculatedMessage.calculated(
				UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816"),
				Instant.parse("2026-08-13T05:00:00Z"),
				"moving-walkway-v1",
				List.of(routeB, routeC),
				AirportRoute.B,
				List.of(train),
				inputs
		);

		assertEquals("2.0", message.schemaVersion());
		assertEquals(CongestionCalculationStatus.CALCULATED, message.status());
		assertEquals(List.of(routeB, routeC), message.routeResults());
		assertEquals(AirportRoute.B, message.recommendedRoute());
		assertEquals(List.of(train), message.railroadArrivals());
		assertEquals(routeB.volumeCapacityRatio(), message.score());
		assertEquals("AVAILABLE", message.level());
	}

	@Test
	void rejectsRecommendedRouteThatIsMissingFromResults() {
		RouteCongestionResult routeB = new RouteCongestionResult(
				AirportRoute.B,
				Instant.EPOCH,
				0,
				0,
				0,
				0,
				0,
				MovingWalkwayStatus.AVAILABLE,
				1,
				1
		);

		assertThrows(IllegalArgumentException.class, () -> CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				Instant.EPOCH,
				"moving-walkway-v1",
				List.of(routeB),
				AirportRoute.C,
				List.of(),
				inputs()
		));
	}

	@Test
	void exposesStableRabbitNames() {
		assertEquals("drrk.congestion.exchange", CongestionRabbitNames.EXCHANGE);
		assertEquals("congestion.snapshot.v2", CongestionRabbitNames.ROUTING_KEY);
		assertEquals("drrk.main.congestion.snapshot.v2", CongestionRabbitNames.MAIN_QUEUE);
		assertEquals("drrk.congestion.dlx", CongestionRabbitNames.DEAD_LETTER_EXCHANGE);
		assertEquals("drrk.main.congestion.snapshot.v2.dlq", CongestionRabbitNames.DEAD_LETTER_QUEUE);
	}

	private CongestionInputReferences inputs() {
		return new CongestionInputReferences(
				Instant.parse("2026-08-13T00:00:00Z"),
				2,
				Instant.parse("2026-08-13T00:00:01Z"),
				3,
				Instant.parse("2026-08-13T00:00:02Z"),
				4,
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				Instant.parse("2026-08-13T00:00:03Z")
		);
	}
}
