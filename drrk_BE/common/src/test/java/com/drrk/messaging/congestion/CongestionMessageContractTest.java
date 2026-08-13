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
		assertEquals("4.0", message.schemaVersion());
		assertEquals("formula-pending-v1", message.calculationVersion());
		assertEquals(CongestionCalculationStatus.FORMULA_PENDING, message.status());
		assertEquals(false, message.sensorDetected());
		assertNull(message.score());
		assertNull(message.level());
		assertNull(message.currentLoad());
		assertNull(message.capacity());
		assertNull(message.forecastLoad());
		assertNull(message.projectedScore());
		assertNull(message.lastTrainDepartureAt());
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
	void createsCalculatedGuideWithLoadCapacityAndRailroadArrivals() {
		CongestionInputReferences inputs = inputs();
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
				"platform-congestion-v1",
				true,
				24.0,
				48L,
				8.5,
				Instant.parse("2026-08-13T04:55:00Z"),
				List.of(train),
				inputs
		);

		assertEquals("4.0", message.schemaVersion());
		assertEquals(CongestionCalculationStatus.CALCULATED, message.status());
		assertEquals(true, message.sensorDetected());
		assertEquals(24.0 / 48.0, message.score());
		assertEquals("MEDIUM", message.level());
		assertEquals(24.0, message.currentLoad());
		assertEquals(48L, message.capacity());
		assertEquals(8.5, message.forecastLoad());
		assertEquals((24.0 + 8.5) / 48.0, message.projectedScore());
		assertEquals(Instant.parse("2026-08-13T04:55:00Z"), message.lastTrainDepartureAt());
		assertEquals(List.of(train), message.railroadArrivals());
	}

	@Test
	void clampsProjectedScoreAtOne() {
		CongestionCalculatedMessage message = CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				Instant.parse("2026-08-13T05:00:00Z"),
				"platform-congestion-v1",
				true,
				60.0,
				48L,
				20.0,
				Instant.parse("2026-08-13T04:55:00Z"),
				List.of(),
				inputs()
		);

		assertEquals(1.0, message.score());
		assertEquals("FULL", message.level());
		assertEquals(1.0, message.projectedScore());
	}

	@Test
	void rejectsCalculatedResultsWithoutCapacityOrDepartureTime() {
		assertThrows(IllegalArgumentException.class, () -> CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				Instant.EPOCH,
				"platform-congestion-v1",
				false,
				10.0,
				0L,
				0.0,
				Instant.EPOCH,
				List.of(),
				inputs()
		));
		assertThrows(IllegalArgumentException.class, () -> CongestionCalculatedMessage.calculated(
				UUID.randomUUID(),
				Instant.EPOCH,
				"platform-congestion-v1",
				false,
				10.0,
				48L,
				0.0,
				null,
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
}
