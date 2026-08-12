package com.drrk.collector.consumer.inference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.drrk.collector.congestion.ModelMeasurementSnapshot;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class InferenceWindowMessageParserTest {

	private final InferenceWindowMessageParser parser = new InferenceWindowMessageParser(new ObjectMapper());

	@Test
	void mapsValidModelWindowToLatestMeasurementSnapshot() {
		ModelMeasurementSnapshot snapshot = parser.parse(validJson());

		assertEquals("8c530c6c-f819-4ad6-b687-760dc698c617", snapshot.messageId());
		assertEquals(Instant.ofEpochMilli(1_755_000_000_000L), snapshot.measuredAt());
		assertEquals(3, snapshot.carrierCount());
		assertEquals(0.42, snapshot.intensity());
	}

	@Test
	void rejectsWindowWhenDerivedCarrierCountDoesNotMatchEvents() {
		String invalid = validJson().replace("\"n_carriers\": 3", "\"n_carriers\": 4");

		assertThrows(InvalidInferenceMessageException.class, () -> parser.parse(invalid));
	}

	@Test
	void rejectsMessageIdThatIsNotUuidVersionFour() {
		String invalid = validJson().replace(
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				"00000000-0000-1000-8000-000000000000"
		);

		assertThrows(InvalidInferenceMessageException.class, () -> parser.parse(invalid));
	}

	private String validJson() {
		return """
				{
				  "message_id": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "space_id": "desk01",
				  "ts": 1755000000.0,
				  "window_sec": 10,
				  "events": [
				    {"t": 1754999993.2, "dur": 3.4, "count": 2, "conf": 0.81, "snr": 24.6},
				    {"t": 1754999997.8, "dur": 2.9, "count": 1, "conf": 0.93, "snr": 21.2}
				  ],
				  "n_events": 2,
				  "n_carriers": 3,
				  "intensity": 0.42,
				  "count_est": null
				}
				""";
	}
}
