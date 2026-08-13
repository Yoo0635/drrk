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
		assertEquals("3.0", message.schemaVersion());
		assertEquals("formula-pending-v0", message.calculationVersion());
		assertEquals(CongestionCalculationStatus.FORMULA_PENDING, message.status());
		assertEquals(false, message.sensorDetected());
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
	void createsCalculatedGuideWithAllAirportRoutesAndSensorDetection() {
		CongestionInputReferences inputs = inputs();
		RouteCongestionResult routeA = new RouteCongestionResult(
				AirportRoute.A,
				Instant.parse("2026-08-13T05:00:20Z"),
				0.0,
				0.0,
				0.0,
				0.0,
				0.0,
				MovingWalkwayStatus.AVAILABLE,
				RouteStatus.CLEAR,
				30,
				90
		);
		RouteCongestionResult routeB = new RouteCongestionResult(
				AirportRoute.B,
				Instant.parse("2026-08-13T05:00:30Z"),
				1.0,
				2.0,
				0.5,
				3.5,
				3.5 / 4.2,
				MovingWalkwayStatus.NORMAL,
				RouteStatus.CONGESTED,
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
				RouteStatus.CLEAR,
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
				true,
				List.of(routeA, routeB, routeC),
				AirportRoute.B,
				List.of(train),
				inputs
		);

		assertEquals("3.0", message.schemaVersion());
		assertEquals(CongestionCalculationStatus.CALCULATED, message.status());
		assertEquals(true, message.sensorDetected());
		assertEquals(List.of(routeA, routeB, routeC), message.routeResults());
		assertEquals(AirportRoute.B, message.recommendedRoute());
		assertEquals(List.of(train), message.railroadArrivals());
		assertEquals(routeB.volumeCapacityRatio(), message.score());
		assertEquals("NORMAL", message.level());
	}

	@Test
	void exposesAirportRoutesAndDemoStatusesInStableOrder() {
		assertEquals(List.of(AirportRoute.A, AirportRoute.B, AirportRoute.C), List.of(AirportRoute.values()));
		assertEquals(List.of(RouteStatus.CLEAR, RouteStatus.CONGESTED), List.of(RouteStatus.values()));
	}

	@Test
	void acceptsNormalStatusForRatioAbovePointSevenThroughOne() {
		RouteCongestionResult result = new RouteCongestionResult(
				AirportRoute.B,
				Instant.EPOCH,
				1.0,
				2.0,
				0.36,
				3.36,
				0.8,
				MovingWalkwayStatus.NORMAL,
				RouteStatus.CLEAR,
				20,
				80
		);

		assertEquals(MovingWalkwayStatus.NORMAL, result.congestionStatus());
	}

	@Test
	void rejectsCalculatedResultsWhenRecommendedRouteIsMissing() {
		RouteCongestionResult routeA = route(AirportRoute.A);
		RouteCongestionResult routeC = route(AirportRoute.C);

		assertThrows(IllegalArgumentException.class, () -> CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				Instant.EPOCH,
				"moving-walkway-v1",
				false,
				List.of(routeA, routeC),
				AirportRoute.B,
				List.of(),
				inputs()
		));
	}

	@Test
	void rejectsCalculatedResultsWithDuplicateAirportRoute() {
		RouteCongestionResult routeA = route(AirportRoute.A);
		RouteCongestionResult routeB = route(AirportRoute.B);
		RouteCongestionResult duplicateRouteB = route(AirportRoute.B);
		RouteCongestionResult routeC = route(AirportRoute.C);

		assertThrows(IllegalArgumentException.class, () -> CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				Instant.EPOCH,
				"moving-walkway-v1",
				false,
				List.of(routeA, routeB, duplicateRouteB, routeC),
				AirportRoute.B,
				List.of(),
				inputs()
		));
	}

	@Test
	void exposesStableRabbitNames() {
		assertEquals("drrk.congestion.exchange", CongestionRabbitNames.EXCHANGE);
		assertEquals("congestion.snapshot.v3", CongestionRabbitNames.ROUTING_KEY);
		assertEquals("drrk.main.congestion.snapshot.v3", CongestionRabbitNames.MAIN_QUEUE);
		assertEquals("drrk.congestion.dlx", CongestionRabbitNames.DEAD_LETTER_EXCHANGE);
		assertEquals("congestion.snapshot.dead.v3", CongestionRabbitNames.DEAD_LETTER_ROUTING_KEY);
		assertEquals("drrk.main.congestion.snapshot.v3.dlq", CongestionRabbitNames.DEAD_LETTER_QUEUE);
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

	private RouteCongestionResult route(AirportRoute route) {
		return new RouteCongestionResult(
				route,
				Instant.EPOCH,
				0,
				0,
				0,
				0,
				0,
				MovingWalkwayStatus.AVAILABLE,
				RouteStatus.CLEAR,
				1,
				1
		);
	}
}
