package com.drrk.collector.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class FormulaPendingCongestionCalculatorTest {

	@Test
	void returnsPendingResultWithInputReferencesInsteadOfInventingAScore() {
		Instant calculatedAt = Instant.parse("2026-08-13T00:10:10Z");
		Instant collectedAt = Instant.parse("2026-08-13T00:10:00Z");
		FormulaPendingCongestionCalculator calculator = new FormulaPendingCongestionCalculator(
				Clock.fixed(calculatedAt, ZoneOffset.UTC),
				() -> UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816")
		);
		CongestionInputs inputs = new CongestionInputs(
				new ArrivalStatusSnapshot(collectedAt, List.of(
						new ArrivalStatusItem("KE001", "1200"),
						new ArrivalStatusItem("KE002", "1205")
				)),
				new PassengerForecastSnapshot(collectedAt, List.of(
						new PassengerForecastItem("1200-1300", 100)
				)),
				new RailroadOperationSnapshot(collectedAt, List.of()),
				new ModelMeasurementSnapshot("model-id", collectedAt.plusSeconds(3), 3, 0.7)
		);

		CongestionCalculatedMessage result = calculator.calculate(inputs);

		assertEquals(CongestionCalculationStatus.FORMULA_PENDING, result.status());
		assertNull(result.score());
		assertNull(result.level());
		assertEquals(2, result.inputs().arrivalStatusItemCount());
		assertEquals(1, result.inputs().passengerForecastItemCount());
		assertEquals(0, result.inputs().railroadOperationItemCount());
		assertEquals("model-id", result.inputs().modelMessageId());
		assertEquals(calculatedAt, result.calculatedAt());
	}
}
