package com.drrk.messaging.congestion;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
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
		assertEquals("1.0", message.schemaVersion());
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
	void exposesStableRabbitNames() {
		assertEquals("drrk.congestion.exchange", CongestionRabbitNames.EXCHANGE);
		assertEquals("congestion.snapshot.v1", CongestionRabbitNames.ROUTING_KEY);
		assertEquals("drrk.main.congestion.snapshot.v1", CongestionRabbitNames.MAIN_QUEUE);
		assertEquals("drrk.congestion.dlx", CongestionRabbitNames.DEAD_LETTER_EXCHANGE);
		assertEquals("drrk.main.congestion.snapshot.dlq", CongestionRabbitNames.DEAD_LETTER_QUEUE);
	}
}
