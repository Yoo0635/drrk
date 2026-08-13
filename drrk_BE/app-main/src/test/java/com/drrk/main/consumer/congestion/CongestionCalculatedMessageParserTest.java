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
	void parsesValidSchemaFourFormulaPendingMessage() {
		CongestionCalculatedMessage message = parser.parse(validJson());

		assertThat(message.messageId()).isEqualTo("8c530c6c-f819-4ad6-b687-760dc698c617");
		assertThat(message.status()).isEqualTo(CongestionCalculationStatus.FORMULA_PENDING);
		assertThat(message.calculatedAt()).isEqualTo(Instant.parse("2026-08-13T03:00:00Z"));
		assertThat(message.inputs().arrivalStatusItemCount()).isEqualTo(2);
		assertThat(message.score()).isNull();
		assertThat(message.currentLoad()).isNull();
	}

	@Test
	void acceptsNonUuidModelMessageIdFromInferencePipeline() {
		CongestionCalculatedMessage message = parser.parse(
				validJson().replace(
						"\"modelMessageId\": \"468c59d4-3b22-44e1-91ed-67b6290fa4a9\"",
						"\"modelMessageId\": \"desk01:1786645389.4\""
				)
		);

		assertThat(message.inputs().modelMessageId()).isEqualTo("desk01:1786645389.4");
	}

	@Test
	void rejectsUnsupportedSchemaVersion() {
		String invalid = validJson().replace("\"schemaVersion\": \"4.0\"", "\"schemaVersion\": \"3.0\"");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	@Test
	void parsesCalculatedMessageWithCurrentAndProjectedLoad() {
		CongestionCalculatedMessage message = parser.parse(calculatedJson());

		assertThat(message.score()).isEqualTo(0.5d);
		assertThat(message.level()).isEqualTo("MEDIUM");
		assertThat(message.currentLoad()).isEqualTo(24.0d);
		assertThat(message.capacity()).isEqualTo(48L);
		assertThat(message.forecastLoad()).isEqualTo(8.5d);
		assertThat(message.projectedScore()).isEqualTo(0.6770833333333334d);
		assertThat(message.lastTrainDepartureAt()).isEqualTo(Instant.parse("2026-08-13T02:55:00Z"));
		assertThat(message.railroadArrivals()).singleElement()
				.extracting(arrival -> arrival.status().name())
				.isEqualTo("SCHEDULED");
	}

	@Test
	void rejectsFormulaPendingMessageWithInventedScore() {
		String invalid = validJson().replace("\"score\": null", "\"score\": 42.0");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	@Test
	void rejectsCalculatedMessageWithInvalidSummary() {
		String invalidCapacity = calculatedJson().replace("\"capacity\": 48", "\"capacity\": 0");
		String invalidProjected = calculatedJson()
				.replace("\"projectedScore\": 0.6770833333333334", "\"projectedScore\": 1.2");

		assertThatThrownBy(() -> parser.parse(invalidCapacity))
				.isInstanceOf(InvalidCongestionMessageException.class);
		assertThatThrownBy(() -> parser.parse(invalidProjected))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	private String validJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "4.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "formula-pending-v1",
				  "status": "FORMULA_PENDING",
				  "sensorDetected": false,
				  "score": null,
				  "level": null,
				  "currentLoad": null,
				  "capacity": null,
				  "forecastLoad": null,
				  "projectedScore": null,
				  "lastTrainDepartureAt": null,
				  "inputs": {
				    "arrivalStatusCollectedAt": "2026-08-13T02:59:00Z",
				    "arrivalStatusItemCount": 2,
				    "passengerForecastCollectedAt": "2026-08-13T02:59:00Z",
				    "passengerForecastItemCount": 1,
				    "railroadOperationCollectedAt": "2026-08-13T02:59:00Z",
				    "railroadOperationItemCount": 3,
				    "modelMessageId": "468c59d4-3b22-44e1-91ed-67b6290fa4a9",
				    "modelMeasuredAt": "2026-08-13T02:59:50Z"
				  },
				  "railroadArrivals": []
				}
				""";
	}

	private String calculatedJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "4.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "platform-congestion-v1",
				  "status": "CALCULATED",
				  "sensorDetected": true,
				  "score": 0.5,
				  "level": "MEDIUM",
				  "currentLoad": 24.0,
				  "capacity": 48,
				  "forecastLoad": 8.5,
				  "projectedScore": 0.6770833333333334,
				  "lastTrainDepartureAt": "2026-08-13T02:55:00Z",
				  "inputs": {
				    "arrivalStatusCollectedAt": "2026-08-13T02:59:00Z",
				    "arrivalStatusItemCount": 2,
				    "passengerForecastCollectedAt": "2026-08-13T02:59:00Z",
				    "passengerForecastItemCount": 1,
				    "railroadOperationCollectedAt": "2026-08-13T02:59:00Z",
				    "railroadOperationItemCount": 3,
				    "modelMessageId": "468c59d4-3b22-44e1-91ed-67b6290fa4a9",
				    "modelMeasuredAt": "2026-08-13T02:59:50Z"
				  },
				  "railroadArrivals": [
				    {"trainNo":"1234","trainType":"일반","scheduledArrivalTime":"14:55","actualArrivalTime":null,"status":"SCHEDULED"}
				  ]
				}
				""";
	}
}
