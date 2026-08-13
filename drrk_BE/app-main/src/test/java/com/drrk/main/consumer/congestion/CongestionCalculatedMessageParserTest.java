package com.drrk.main.consumer.congestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import com.drrk.messaging.congestion.RouteStatus;
import java.time.Instant;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class CongestionCalculatedMessageParserTest {

	private final CongestionCalculatedMessageParser parser = new CongestionCalculatedMessageParser(new ObjectMapper());

	@Test
	void parsesValidSchemaThreeFormulaPendingMessage() {
		CongestionCalculatedMessage message = parser.parse(validJson());

		assertThat(message.messageId()).isEqualTo("8c530c6c-f819-4ad6-b687-760dc698c617");
		assertThat(message.status()).isEqualTo(CongestionCalculationStatus.FORMULA_PENDING);
		assertThat(message.calculatedAt()).isEqualTo(Instant.parse("2026-08-13T03:00:00Z"));
		assertThat(message.inputs().arrivalStatusItemCount()).isEqualTo(2);
		assertThat(message.score()).isNull();
	}

	@Test
	void rejectsUnsupportedSchemaVersion() {
		String invalid = validJson().replace("\"schemaVersion\": \"3.0\"", "\"schemaVersion\": \"2.0\"");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	@Test
	void parsesCalculatedMessageWithEachRouteAndDetectedSensorStatus() {
		CongestionCalculatedMessage message = parser.parse(calculatedJson());

		assertThat(message.recommendedRoute()).hasToString("C");
		assertThat(message.sensorDetected()).isTrue();
		assertThat(message.routeResults()).hasSize(3)
				.extracting(result -> result.status())
				.containsExactly(RouteStatus.CLEAR, RouteStatus.CONGESTED, RouteStatus.CLEAR);
		assertThat(message.railroadArrivals()).singleElement()
				.extracting(arrival -> arrival.status().name())
				.isEqualTo("SCHEDULED");
	}

	@Test
	void rejectsCalculatedMessageWithoutEachOfRoutesAThroughC() {
		String invalid = calculatedJson().replace("\"route\":\"A\"", "\"route\":\"B\"");

		assertThatThrownBy(() -> parser.parse(invalid))
				.isInstanceOf(InvalidCongestionMessageException.class);
	}

	@Test
	void rejectsCalculatedMessageWhenSensorStatusDoesNotMatchRoutes() {
		String undetectedWithCongestedB = calculatedJson()
				.replace("\"sensorDetected\": true", "\"sensorDetected\": false");
		String detectedWithClearB = calculatedJson()
				.replaceFirst("\"status\":\"CONGESTED\"", "\"status\":\"CLEAR\"");

		assertThatThrownBy(() -> parser.parse(undetectedWithCongestedB))
				.isInstanceOf(InvalidCongestionMessageException.class);
		assertThatThrownBy(() -> parser.parse(detectedWithClearB))
				.isInstanceOf(InvalidCongestionMessageException.class);
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
				"\"recommendedRoute\": \"C\"",
				"\"recommendedRoute\": \"B\""
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
				  "schemaVersion": "3.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "formula-pending-v0",
				  "status": "FORMULA_PENDING",
				  "sensorDetected": false,
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
				  "schemaVersion": "3.0",
				  "calculatedAt": "2026-08-13T03:00:00Z",
				  "calculationVersion": "moving-walkway-v1",
				  "status": "CALCULATED",
				  "sensorDetected": true,
				  "score": 1.4285714285714286,
				  "level": "CONGESTED",
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
				    {"route":"A","walkwayArrivalTime":"2026-08-13T03:01:00Z","stay":1,"incoming":1,"residual":1,"load":3,"volumeCapacityRatio":0.7142857142857143,"congestionStatus":"NORMAL","status":"CLEAR","passageTimeSeconds":422,"totalTravelTimeSeconds":509},
				    {"route":"B","walkwayArrivalTime":"2026-08-13T03:01:10Z","stay":1,"incoming":1,"residual":1,"load":3,"volumeCapacityRatio":0.7142857142857143,"congestionStatus":"NORMAL","status":"CONGESTED","passageTimeSeconds":110,"totalTravelTimeSeconds":480},
				    {"route":"C","walkwayArrivalTime":"2026-08-13T03:01:20Z","stay":2,"incoming":2,"residual":2,"load":6,"volumeCapacityRatio":1.4285714285714286,"congestionStatus":"CONGESTED","status":"CLEAR","passageTimeSeconds":347,"totalTravelTimeSeconds":434}
				  ],
				  "recommendedRoute": "C",
				  "railroadArrivals": [
				    {"trainNo":"1234","trainType":"일반","scheduledArrivalTime":"14:55","actualArrivalTime":null,"status":"SCHEDULED"}
				  ]
				}
				""";
	}
}
