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
		assertEquals("desk01", snapshot.spaceId());
		assertEquals(10, snapshot.windowSec());
		assertEquals(3, snapshot.carrierCount());
	}

	@Test
	void acceptsCalculationContractWithoutLegacyMetadataWhenFallbackMessageIdIsValidUuidV4() {
		ModelMeasurementSnapshot snapshot = parser.parse(minimalCalculationJson(),
				"0f37c542-1fc9-4d50-9f1c-53ebda7edc4c");

		assertEquals("0f37c542-1fc9-4d50-9f1c-53ebda7edc4c", snapshot.messageId());
		assertEquals("desk01", snapshot.spaceId());
		assertEquals(10, snapshot.windowSec());
		assertEquals(3, snapshot.carrierCount());
		assertEquals(Instant.ofEpochMilli(1_755_000_000_000L), snapshot.measuredAt());
	}

	@Test
	void prefersValidJsonMessageIdOverFallbackMessageId() {
		ModelMeasurementSnapshot snapshot = parser.parse(validJson(),
				"0f37c542-1fc9-4d50-9f1c-53ebda7edc4c");

		assertEquals("8c530c6c-f819-4ad6-b687-760dc698c617", snapshot.messageId());
	}

	@Test
	void rejectsWhenNeitherJsonMessageIdNorFallbackMessageIdIsValidUuidV4() {
		assertThrows(InvalidInferenceMessageException.class,
				() -> parser.parse(minimalCalculationJson(), "not-a-uuid"));
	}

	@Test
	void rejectsParseWithoutFallbackWhenJsonMessageIdIsMissing() {
		assertThrows(InvalidInferenceMessageException.class, () -> parser.parse(minimalCalculationJson()));
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

	@Test
	void rejectsNegativeEventCountEvenWhenAggregateCountMatches() {
		String invalid = validJson()
				.replace("\"count\": 2", "\"count\": -1")
				.replace("\"n_carriers\": 3", "\"n_carriers\": 0");

		assertThrows(InvalidInferenceMessageException.class, () -> parser.parse(invalid));
	}

	@Test
	void rejectsNullEvents() {
		String invalid = """
				{
				  "space_id": "desk01",
				  "ts": 1755000000.0,
				  "window_sec": 10,
				  "events": null,
				  "n_carriers": 3
				}
				""";

		assertThrows(InvalidInferenceMessageException.class,
				() -> parser.parse(invalid, "0f37c542-1fc9-4d50-9f1c-53ebda7edc4c"));
	}

	@Test
	void rejectsEventTimeOutsideMeasurementWindow() {
		String invalid = minimalCalculationJson().replace("1754999993.2", "1754999989.9");

		assertThrows(InvalidInferenceMessageException.class,
				() -> parser.parse(invalid, "0f37c542-1fc9-4d50-9f1c-53ebda7edc4c"));
	}

	@Test
	void rejectsEventDurationLongerThanWindow() {
		String invalid = minimalCalculationJson().replace("\"dur\": 3.4", "\"dur\": 10.1");

		assertThrows(InvalidInferenceMessageException.class,
				() -> parser.parse(invalid, "0f37c542-1fc9-4d50-9f1c-53ebda7edc4c"));
	}

	@Test
	void rejectsMissingRequiredNumericFieldsInsteadOfTreatingThemAsZero() {
		String missingTimestamp = minimalCalculationJson().replace("\"ts\": 1755000000.0,", "");
		String missingEventCount = minimalCalculationJson().replace("\"count\": 2,", "");

		assertThrows(InvalidInferenceMessageException.class,
				() -> parser.parse(missingTimestamp, "0f37c542-1fc9-4d50-9f1c-53ebda7edc4c"));
		assertThrows(InvalidInferenceMessageException.class,
				() -> parser.parse(missingEventCount, "0f37c542-1fc9-4d50-9f1c-53ebda7edc4c"));
	}

	@Test
	void sumsCarrierCountsAsLongWithoutOverflow() {
		String json = """
				{
				  "space_id": "desk01",
				  "ts": 1755000000.0,
				  "window_sec": 10,
				  "events": [
				    {"t": 1754999993.2, "dur": 3.4, "count": 2147483647, "conf": 0.81},
				    {"t": 1754999997.8, "dur": 2.9, "count": 1, "conf": 0.93}
				  ],
				  "n_carriers": 2147483648
				}
				""";

		ModelMeasurementSnapshot snapshot = parser.parse(json, "0f37c542-1fc9-4d50-9f1c-53ebda7edc4c");

		assertEquals(2_147_483_648L, snapshot.carrierCount());
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

	private String minimalCalculationJson() {
		return """
				{
				  "space_id": "desk01",
				  "ts": 1755000000.0,
				  "window_sec": 10,
				  "events": [
				    {"t": 1754999993.2, "dur": 3.4, "count": 2, "conf": 0.81},
				    {"t": 1754999997.8, "dur": 2.9, "count": 1, "conf": 0.93}
				  ],
				  "n_carriers": 3
				}
				""";
	}
}
