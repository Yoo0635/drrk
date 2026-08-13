package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
import com.drrk.messaging.congestion.RouteStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class MovingWalkwayCongestionCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-08-13T05:00:00Z");
	private static final UUID MESSAGE_ID = UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816");

	@Test
	void calculatesFixedRouteTimesAndRecommendsBWhenSensorIsNotDetected() {
		CongestionCalculatedMessage result = calculator().calculate(inputs(0, List.of()));

		assertFalse(result.sensorDetected());
		assertEquals(AirportRoute.B, result.recommendedRoute());
		assertRoute(result, AirportRoute.A, RouteStatus.CLEAR, 0, 509);
		assertRoute(result, AirportRoute.B, RouteStatus.CLEAR, 46, 416);
		assertRoute(result, AirportRoute.C, RouteStatus.CLEAR, 0, 434);
	}

	@ParameterizedTest
	@ValueSource(ints = {1, 7})
	void detectsEveryPositiveCarrierCountAndOnlyChangesRouteB(int carrierCount) {
		CongestionCalculatedMessage result = calculator().calculate(inputs(carrierCount, List.of()));

		assertTrue(result.sensorDetected());
		assertEquals(AirportRoute.C, result.recommendedRoute());
		assertRoute(result, AirportRoute.A, RouteStatus.CLEAR, 0, 509);
		assertRoute(result, AirportRoute.B, RouteStatus.CONGESTED, 110, 480);
		assertRoute(result, AirportRoute.C, RouteStatus.CLEAR, 0, 434);
	}

	@Test
	void keepsDiagnosticValuesAndRailroadArrivalMapping() {
		CongestionCalculatedMessage result = calculator().calculate(inputs(
				1,
				List.of(
						new RailroadOperationItem("1001", "110", "20260813135900", null, "일반"),
						new RailroadOperationItem("1002", "110", "20260813150000", null, "직통"),
						new RailroadOperationItem("1003", "110", "20260813130000", "20260813140500", "일반")
				)
		));

		RouteCongestionResult routeB = route(result, AirportRoute.B);
		assertEquals(1, routeB.stay());
		assertEquals(0, routeB.incoming());
		assertEquals(0, routeB.residual());
		assertEquals(1, routeB.load());
		assertEquals(1 / 4.2, routeB.volumeCapacityRatio(), 1.0e-9);
		assertEquals(MovingWalkwayStatus.AVAILABLE, routeB.congestionStatus());
		assertEquals(List.of("1003", "1001", "1002"), result.railroadArrivals().stream()
				.map(arrival -> arrival.trainNo())
				.toList());
		assertEquals(RailroadArrivalStatus.ARRIVED, result.railroadArrivals().get(0).status());
		assertEquals("14:05", result.railroadArrivals().get(0).actualArrivalTime());
		assertEquals(RailroadArrivalStatus.DELAYED, result.railroadArrivals().get(1).status());
		assertEquals(RailroadArrivalStatus.SCHEDULED, result.railroadArrivals().get(2).status());
	}

	private void assertRoute(
			CongestionCalculatedMessage message,
			AirportRoute airportRoute,
			RouteStatus status,
			long passageTimeSeconds,
			long totalTravelTimeSeconds
	) {
		RouteCongestionResult route = route(message, airportRoute);
		assertEquals(status, route.status());
		assertEquals(passageTimeSeconds, route.passageTimeSeconds());
		assertEquals(totalTravelTimeSeconds, route.totalTravelTimeSeconds());
	}

	private RouteCongestionResult route(CongestionCalculatedMessage message, AirportRoute airportRoute) {
		return message.routeResults().stream()
				.filter(result -> result.route() == airportRoute)
				.findFirst()
				.orElseThrow();
	}

	private MovingWalkwayCongestionCalculator calculator() {
		return new MovingWalkwayCongestionCalculator(
				Clock.fixed(NOW, ZoneOffset.UTC),
				() -> MESSAGE_ID
		);
	}

	private CongestionInputs inputs(int carriers, List<RailroadOperationItem> trains) {
		return new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), trains),
				new ModelMeasurementSnapshot(
						"8c530c6c-f819-4ad6-b687-760dc698c617",
						NOW.minusSeconds(5),
						"desk01",
						10,
						carriers
				)
		);
	}
}
