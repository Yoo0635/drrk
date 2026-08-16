package com.drrk.main.consumer.inference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.json.JsonMapper;

class InferenceWindowMessageParserTest {

	private static final String VALID_MESSAGE = """
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
	private static final String MODEL_MESSAGE_ID = "desk01:1786634462.6";

	private final InferenceWindowMessageParser parser = new InferenceWindowMessageParser(new JsonMapper());

	@Test
	void parsesEveryModelFieldWithoutRenamingOrDroppingIt() {
		InferenceWindowMessage message = parser.parse(VALID_MESSAGE);

		assertThat(message.messageId()).isEqualTo("8c530c6c-f819-4ad6-b687-760dc698c617");
		assertThat(message.spaceId()).isEqualTo("desk01");
		assertThat(message.ts()).isEqualTo(1755000000.0);
		assertThat(message.windowSec()).isEqualTo(10);
		assertThat(message.events()).containsExactly(
				new InferenceEvent(1754999993.2, 3.4, 2, 0.81, 24.6),
				new InferenceEvent(1754999997.8, 2.9, 1, 0.93, 21.2)
		);
		assertThat(message.nEvents()).isEqualTo(2);
		assertThat(message.nCarriers()).isEqualTo(3);
		assertThat(message.intensity()).isEqualTo(0.42);
		assertThat(message.countEst()).isNull();
	}

	@Test
	void usesNonUuidAmqpMessageIdWhenJsonMessageIdIsMissing() {
		String withoutMessageId = VALID_MESSAGE.replace(
				"  \"message_id\": \"8c530c6c-f819-4ad6-b687-760dc698c617\",%n".formatted(),
				""
		);

		InferenceWindowMessage message = parser.parse(withoutMessageId, MODEL_MESSAGE_ID);

		assertThat(message.messageId()).isEqualTo(MODEL_MESSAGE_ID);
		assertThat(message.spaceId()).isEqualTo("desk01");
	}

	@Test
	void rejectsRenamedFieldsBecauseWireContractMustMatchModelOutput() {
		String renamed = VALID_MESSAGE
				.replace("\"ts\"", "\"timestamp\"")
				.replace("\"t\"", "\"started_at\"");

		assertThatThrownBy(() -> parser.parse(renamed))
				.isInstanceOf(InvalidInferenceMessageException.class)
				.hasMessageContaining("fields");
	}

	@Test
	void rejectsDerivedCountsThatDisagreeWithEvents() {
		String inconsistent = VALID_MESSAGE.replace("\"n_carriers\": 3", "\"n_carriers\": 4");

		assertThatThrownBy(() -> parser.parse(inconsistent))
				.isInstanceOf(InvalidInferenceMessageException.class)
				.hasMessageContaining("n_carriers");
	}

	@Test
	void rejectsWhenNeitherJsonNorAmqpMessageIdIsPresent() {
		String withoutMessageId = VALID_MESSAGE.replace(
				"  \"message_id\": \"8c530c6c-f819-4ad6-b687-760dc698c617\",%n".formatted(),
				""
		);

		assertThatThrownBy(() -> parser.parse(withoutMessageId))
				.isInstanceOf(InvalidInferenceMessageException.class)
				.hasMessageContaining("message_id");
	}

	@Test
	void rejectsOutOfRangeEventValues() {
		String invalidDuration = VALID_MESSAGE.replace("\"dur\": 3.4", "\"dur\": 6.1");

		assertThatThrownBy(() -> parser.parse(invalidDuration))
				.isInstanceOf(InvalidInferenceMessageException.class)
				.hasMessageContaining("dur");
	}
}
