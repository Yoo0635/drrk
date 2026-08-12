package com.drrk.collector.client.airport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.drrk.collector.congestion.ArrivalStatusItem;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import com.drrk.collector.congestion.PassengerForecastItem;
import com.drrk.collector.congestion.PassengerForecastSnapshot;
import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

class AirportResponseMapperTest {

	private final ObjectMapper objectMapper = new ObjectMapper();
	private final Instant collectedAt = Instant.parse("2026-08-13T00:00:00Z");

	@Test
	void arrivalMapperKeepsOnlyTerminalOneGateBSelectedFields() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE.", "ignored": "value"},
				    "body": {"items": [
				      {"terno": "T1", "entrygate": " b ", "flightid": "KE001", "estimatedtime": "202608131200", "korean": "10.0"},
				      {"terno": "T1", "entrygate": "A", "flightid": "KE002", "estimatedtime": "202608131205"},
				      {"terno": "T2", "entrygate": "B", "flightid": "KE003", "estimatedtime": "202608131210"}
				    ], "totalCount": 3}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		assertEquals(collectedAt, snapshot.collectedAt());
		assertEquals(List.of(new ArrivalStatusItem("KE001", "202608131200")), snapshot.items());
	}

	@Test
	void passengerMapperUsesTerminalOneEntryTotal() throws Exception {
		PassengerForecastApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": [
				      {"adate": "20260813", "atime": "00_01", "t1egsum1": "722.0", "t1dgsum1": "627.0"}
				    ]}
				  }
				}
				""", PassengerForecastApiResponse.class);

		PassengerForecastSnapshot snapshot = new PassengerForecastMapper().map(response, collectedAt);

		assertEquals(List.of(new PassengerForecastItem("00_01", 722)), snapshot.items());
	}

	@Test
	void railroadMapperUsesTrainNumberAndPlannedTimes() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": [
				      {"trnNo": "A2002", "planArrvDttm": "", "planDptrDttm": "20260813050800", "stnCd": "110"}
				    ]}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(
				List.of(new RailroadOperationItem("A2002", "20260813050800", "")),
				snapshot.items()
		);
	}

	@Test
	void mapperRejectsPublicApiFailureResultCode() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR."},"body":{"items":[]}}}
				""", ArrivalStatusApiResponse.class);

		assertThrows(AirportApiResponseException.class, () -> new ArrivalStatusMapper().map(response, collectedAt));
	}
}
