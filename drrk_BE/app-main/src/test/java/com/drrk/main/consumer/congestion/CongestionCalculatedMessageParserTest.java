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
		String invalid = validJson().replace("\"schemaVersion\": \"2.0\"", "\"schemaVersion\": \"1.0\"");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	@Test
	void parsesCalculatedRouteAndRailroadGuide() {
		CongestionCalculatedMessage message = parser.parse(calculatedJson());

		assertThat(message.recommendedRoute()).hasToString("B");
		assertThat(message.routeResults()).hasSize(2);
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
	void rejectsCalculatedMessageWithInconsistentFormulaOrRecommendation() {
		String invalidLoad = calculatedJson().replace("\"load\":3", "\"load\":4");
		String invalidRecommendation = calculatedJson().replace(
				"\"recommendedRoute\": \"B\"",
				"\"recommendedRoute\": \"C\""
		);

		assertThatThrownBy(() -> parser.parse(invalidLoad))
				.isInstanceOf(InvalidCongestionMessageException.class);
		assertThatThrownBy(() -> parser.parse(invalidRecommendation))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	private String validJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "2.0",
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

	private String calculatedJson() {
		return """
				{
				  "messageId": "8c530c6c-f819-4ad6-b687-760dc698c617",
				  "schemaVersion": "2.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "moving-walkway-v1",
				  "status": "CALCULATED",
				  "score": 0.7142857142857143,
				  "level": "AVAILABLE",
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
				  "routeResults": [
				    {"route":"B","walkwayArrivalTime":"2026-08-13T03:01:00Z","stay":1,"incoming":1,"residual":1,"load":3,"volumeCapacityRatio":0.7142857142857143,"congestionStatus":"AVAILABLE","passageTimeSeconds":20,"totalTravelTimeSeconds":80},
				    {"route":"C","walkwayArrivalTime":"2026-08-13T03:01:10Z","stay":2,"incoming":2,"residual":2,"load":6,"volumeCapacityRatio":1.4285714285714286,"congestionStatus":"CONGESTED","passageTimeSeconds":40,"totalTravelTimeSeconds":100}
				  ],
				  "recommendedRoute": "B",
				  "railroadArrivals": [
				    {"trainNo":"1234","trainType":"일반","scheduledArrivalTime":"14:55","actualArrivalTime":null,"status":"SCHEDULED"}
				  ]
				}
				""";
	}
}
