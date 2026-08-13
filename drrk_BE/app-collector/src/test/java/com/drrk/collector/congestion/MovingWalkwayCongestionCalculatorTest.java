package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class MovingWalkwayCongestionCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-08-13T05:00:00Z");
	private static final UUID MESSAGE_ID = UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816");

	@Test
	void calculatesArrivalCongestionAndRecommendsShorterActualRoute() {
		MovingWalkwayCongestionCalculator calculator = calculator(
				new MovingWalkwayRouteDefinition(AirportRoute.B, 0.5, 100, 20, 10, 30, 50),
				new MovingWalkwayRouteDefinition(AirportRoute.C, 0.2, 100, 30, 20, 40, 20)
		);

		CongestionCalculatedMessage result = calculator.calculate(inputs(
				4,
				List.of(
						new ArrivalStatusItem("B", "KE001", "202608131400", 30, 20),
						new ArrivalStatusItem("B", "KE001", "202608131405", 30, 20),
						new ArrivalStatusItem("C", "OZ999", "202608131500", 90, 10)
				),
				List.of(new PassengerForecastItem("20260813", "14_15", 180)),
				List.of()
		));

		RouteCongestionResult routeB = result.routeResults().get(0);
		assertEquals(3.2, routeB.stay(), 1.0e-9);
		assertEquals(2.5, routeB.incoming(), 1.0e-9);
		assertEquals(0.05, routeB.residual(), 1.0e-9);
		assertEquals(5.75, routeB.load(), 1.0e-9);
		assertEquals(MovingWalkwayStatus.CONGESTED, routeB.congestionStatus());
		assertEquals(30, routeB.passageTimeSeconds());
		assertEquals(100, routeB.totalTravelTimeSeconds());

		RouteCongestionResult routeC = result.routeResults().get(1);
		assertEquals(3.83, routeC.load(), 1.0e-9);
		assertEquals(MovingWalkwayStatus.AVAILABLE, routeC.congestionStatus());
		assertEquals(70, routeC.totalTravelTimeSeconds());
		assertEquals(AirportRoute.C, result.recommendedRoute());
	}

	@Test
	void treatsVolumeCapacityRatioEqualToOneAsAvailable() {
		MovingWalkwayCongestionCalculator calculator = calculator(
				new MovingWalkwayRouteDefinition(AirportRoute.B, 1.0, 10, 10, 7, 9, 0),
				new MovingWalkwayRouteDefinition(AirportRoute.C, 1.0, 10, 10, 7, 9, 0)
		);

		CongestionCalculatedMessage result = calculator.calculate(inputs(
				0,
				List.of(new ArrivalStatusItem("B", "KE001", "202608131400", 42, 0)),
				List.of(),
				List.of()
		));

		RouteCongestionResult routeB = result.routeResults().get(0);
		assertEquals(4.2, routeB.load(), 1.0e-9);
		assertEquals(1.0, routeB.volumeCapacityRatio(), 1.0e-9);
		assertEquals(MovingWalkwayStatus.AVAILABLE, routeB.congestionStatus());
		assertEquals(AirportRoute.B, result.recommendedRoute());
	}

	@Test
	void mapsRailroadArrivalStatusUsingActualThenScheduledTime() {
		MovingWalkwayCongestionCalculator calculator = calculator(
				new MovingWalkwayRouteDefinition(AirportRoute.B, 0, 10, 1, 1, 1, 0),
				new MovingWalkwayRouteDefinition(AirportRoute.C, 0, 10, 2, 1, 1, 0)
		);

		CongestionCalculatedMessage result = calculator.calculate(inputs(
				0,
				List.of(),
				List.of(),
				List.of(
						new RailroadOperationItem("1001", "110", "20260813135900", null, "일반"),
						new RailroadOperationItem("1002", "110", "20260813150000", null, "직통"),
						new RailroadOperationItem("1003", "110", "20260813130000", "20260813140500", "일반")
				)
		));

		assertEquals(List.of("1003", "1001", "1002"), result.railroadArrivals().stream()
				.map(arrival -> arrival.trainNo())
				.toList());
		assertEquals(RailroadArrivalStatus.ARRIVED, result.railroadArrivals().get(0).status());
		assertEquals("14:05", result.railroadArrivals().get(0).actualArrivalTime());
		assertEquals(RailroadArrivalStatus.DELAYED, result.railroadArrivals().get(1).status());
		assertEquals(RailroadArrivalStatus.SCHEDULED, result.railroadArrivals().get(2).status());
	}

	private MovingWalkwayCongestionCalculator calculator(
			MovingWalkwayRouteDefinition routeB,
			MovingWalkwayRouteDefinition routeC
	) {
		return new MovingWalkwayCongestionCalculator(
				Clock.fixed(NOW, ZoneOffset.UTC),
				() -> MESSAGE_ID,
				0.1,
				routeB,
				routeC
		);
	}

	private CongestionInputs inputs(
			int carriers,
			List<ArrivalStatusItem> arrivals,
			List<PassengerForecastItem> forecasts,
			List<RailroadOperationItem> trains
	) {
		return new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), arrivals),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), forecasts),
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
