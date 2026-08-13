package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PlatformCongestionCalculatorTest {

	private static final Instant NOW = Instant.parse("2026-08-13T05:00:00Z");
	private static final UUID MESSAGE_ID = UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816");

	@Test
	void calculatesCurrentAndProjectedPlatformCongestionFromSensorHistoryAndFlightForecast() {
		PlatformCongestionCalculator calculator = new PlatformCongestionCalculator(
				Clock.fixed(NOW, ZoneOffset.UTC),
				() -> MESSAGE_ID,
				properties()
		);

		CongestionCalculatedMessage result = calculator.calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of(
						new ArrivalStatusItem("B", "KE001", "202608131410", 100, 50),
						new ArrivalStatusItem("C", "KE002", "202608131500", 30, 20)
				)),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of(
						new PassengerForecastItem("20260813", "14_15", 300)
				)),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0900", "110", "20260813135500", null, "20260813135500", "20260813135500", "일반"),
						new RailroadOperationItem("A0901", "110", "20260813140500", null, "20260813140500", "20260813140500", "일반")
				)),
				List.of(
						new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:46:00Z"), "desk01", 60, 5),
						new ModelMeasurementSnapshot("m2", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7),
						new ModelMeasurementSnapshot("m3", Instant.parse("2026-08-13T04:53:00Z"), "desk01", 60, 11)
				)
		));

		assertTrue(result.sensorDetected());
		assertEquals(12.0, result.currentLoad());
		assertEquals(48L, result.capacity());
		assertEquals(12.0 / 48.0, result.score(), 1.0e-9);
		assertEquals("LOW", result.level());
		assertEquals(Instant.parse("2026-08-13T04:55:00Z"), result.lastTrainDepartureAt());
		assertEquals(34.4, result.forecastLoad(), 1.0e-9);
		assertEquals((12.0 + 34.4) / 48.0, result.projectedScore(), 1.0e-9);
		assertEquals(List.of("A0900", "A0901"), result.railroadArrivals().stream()
				.map(arrival -> arrival.trainNo())
				.toList());
		assertEquals(RailroadArrivalStatus.DELAYED, result.railroadArrivals().get(0).status());
		assertEquals(RailroadArrivalStatus.SCHEDULED, result.railroadArrivals().get(1).status());
	}

	@Test
	void returnsFormulaPendingWhenNoDepartedTrainExistsYet() {
		PlatformCongestionCalculator calculator = new PlatformCongestionCalculator(
				Clock.fixed(NOW, ZoneOffset.UTC),
				() -> MESSAGE_ID,
				properties()
		);

		CongestionCalculatedMessage result = calculator.calculate(new CongestionInputs(
				new ArrivalStatusSnapshot(NOW.minusSeconds(60), List.of()),
				new PassengerForecastSnapshot(NOW.minusSeconds(60), List.of()),
				new RailroadOperationSnapshot(NOW.minusSeconds(60), List.of(
						new RailroadOperationItem("A0901", "110", "20260813140500", null, "20260813140500", "20260813140500", "일반")
				)),
				List.of(new ModelMeasurementSnapshot("m1", Instant.parse("2026-08-13T04:49:00Z"), "desk01", 60, 7))
		));

		assertEquals("FORMULA_PENDING", result.status().name());
	}

	private CongestionCalculationProperties properties() {
		CongestionCalculationProperties properties = new CongestionCalculationProperties();
		properties.setTrainCapacity(48);
		properties.setWalkMinutes(10);
		properties.setForecastLeadMinutes(43);
		properties.setForecastDistributionMinutes(14);
		properties.setRK(0.09);
		properties.setRF(0.22);
		properties.setCK(0.75);
		properties.setCF(0.95);
		return properties;
	}
}
