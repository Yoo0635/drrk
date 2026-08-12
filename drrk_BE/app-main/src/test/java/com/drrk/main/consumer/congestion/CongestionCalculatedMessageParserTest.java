package com.drrk.main.consumer.congestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CongestionCalculatedMessageParserTest {

	private final CongestionCalculatedMessageParser parser = new CongestionCalculatedMessageParser(new ObjectMapper());

	@Test
	void parsesValidFormulaPendingMessage() {
		CongestionCalculatedMessage message = parser.parse(validJson());

		assertThat(message.messageId()).isEqualTo("8c530c6c-f819-4ad6-b687-760dc698c617");
		assertThat(message.status()).isEqualTo(CongestionCalculationStatus.FORMULA_PENDING);
		assertThat(message.calculatedAt()).isEqualTo(Instant.parse("2026-08-13T03:00:00Z"));
		assertThat(message.inputs().arrivalStatusItemCount()).isEqualTo(2);
		assertThat(message.score()).isNull();
	}

	@Test
	void rejectsUnsupportedSchemaVersion() {
		String invalid = validJson().replace("\"schemaVersion\": \"1.0\"", "\"schemaVersion\": \"2.0\"");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	@Test
	void rejectsFormulaPendingMessageWithInventedScore() {
		String invalid = validJson().replace("\"score\": null", "\"score\": 42.0");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	private String validJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "1.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "formula-pending-v0",
				  "status": "FORMULA_PENDING",
				  "score": null,
				  "level": null,
				  "inputs": {
				    "arrivalStatusCollectedAt": "2026-08-13T02:59:00Z",
				    "arrivalStatusItemCount": 2,
				    "passengerForecastCollectedAt": "2026-08-13T02:59:00Z",
				    "passengerForecastItemCount": 1,
				    "railroadOperationCollectedAt": "2026-08-13T02:59:00Z",
				    "railroadOperationItemCount": 3,
				    "modelMessageId": "468c59d4-3b22-44e1-91ed-67b6290fa4a9",
				    "modelMeasuredAt": "2026-08-13T02:59:50Z"
				  }
				}
				""";
	}
}
