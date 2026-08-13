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
	void arrivalMapperKeepsTerminalOneGatesBAndCWithPassengerCounts() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE.", "ignored": "value"},
				    "body": {"items": [
				      {"terno": "T1", "entrygate": " b ", "flightid": "KE001", "estimatedtime": "202608131200", "scheduletime": "202608131159", "korean": "10", "foreigner": "4"},
				      {"terno": "t1", "entrygate": " c ", "flightid": "OZ002", "estimatedtime": "", "scheduletime": "202608131205", "korean": "0", "foreigner": "7"},
				      {"terno": "T1", "entrygate": "A", "flightid": "KE002", "estimatedtime": "202608131205"},
				      {"terno": "T2", "entrygate": "B", "flightid": "KE003", "estimatedtime": "202608131210"},
				      {"terno": "T1", "entrygate": "B", "flightid": "KE004", "estimatedtime": "", "scheduletime": "", "korean": "1", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "B", "flightid": "KE005", "estimatedtime": "202608131215", "korean": "10.0", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "C", "flightid": "KE006", "estimatedtime": "202608131220", "korean": "-1", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "C", "flightid": "KE007", "estimatedtime": "not-a-time", "korean": "1", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "C", "flightid": "", "estimatedtime": "202608131225", "korean": "1", "foreigner": "1"}
				    ], "totalCount": 9}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		assertEquals(collectedAt, snapshot.collectedAt());
		assertEquals(List.of(
				new ArrivalStatusItem("B", "KE001", "202608131200", 10, 4)
		), snapshot.items());
	}

	@Test
	void arrivalMapperKeepsCurrentAndNextFiveFlightsOrderedByEstimatedTime() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": [
				      {"terno":"T1","entrygate":"B","flightid":"KE0906","estimatedtime":"202608130906","korean":"6","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0859","estimatedtime":"202608130859","korean":"59","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0902","estimatedtime":"202608130902","korean":"2","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0900","estimatedtime":"202608130900","korean":"0","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0905","estimatedtime":"202608130905","korean":"5","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0901","estimatedtime":"202608130901","korean":"1","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0904","estimatedtime":"202608130904","korean":"4","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0903","estimatedtime":"202608130903","korean":"3","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0907","estimatedtime":"202608130907","korean":"7","foreigner":"0"}
				    ]}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		assertEquals(List.of(
				new ArrivalStatusItem("C", "KE0900", "202608130900", 0, 0),
				new ArrivalStatusItem("C", "KE0901", "202608130901", 1, 0),
				new ArrivalStatusItem("B", "KE0902", "202608130902", 2, 0),
				new ArrivalStatusItem("C", "KE0903", "202608130903", 3, 0),
				new ArrivalStatusItem("B", "KE0904", "202608130904", 4, 0)
		), snapshot.items());
	}

	@Test
	void passengerMapperUsesTerminalOneEntryGateOneExactCounts() throws Exception {
		PassengerForecastApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": [
				      {"adate": "20260813", "atime": "00_01", "t1eg1": "722", "t1egsum1": "999.0"},
				      {"adate": "20260813", "atime": "01_02", "t1eg1": "722.0"},
				      {"adate": "20260813", "atime": "02_03", "t1eg1": "-1"}
				    ]}
				  }
				}
				""", PassengerForecastApiResponse.class);

		PassengerForecastSnapshot snapshot = new PassengerForecastMapper().map(response, collectedAt);

		assertEquals(List.of(new PassengerForecastItem("20260813", "00_01", 722)), snapshot.items());
	}

	@Test
	void railroadMapperUsesStationTimesAndTrainType() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": [
				      {"trnNo": "A2002", "stnCd": " 110 ", "accomArrvDttm": "20260813090900", "accomDptrDttm": "20260813091000", "trnClsfNm": "AREX"},
				      {"trnNo": "A2003", "stnCd": "111", "accomArrvDttm": "20260813100800", "accomDptrDttm": "20260813100900", "trnClsNm": "LOCAL"},
				      {"trnNo": "A2004", "stnCd": "112", "accomArrvDttm": "", "accomDptrDttm": "20260813110900", "trnClsfNm": "LOCAL"}
				    ]}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(
				List.of(
						new RailroadOperationItem(
								"A2002", "110", "20260813090900", null, "20260813091000", "AREX"
						),
						new RailroadOperationItem(
								"A2003", "111", "20260813100800", null, "20260813100900", "LOCAL"
						)
				),
				snapshot.items()
		);
	}

	@Test
	void railroadMapperKeepsCurrentAndNextFiveTrainsOrderedByArrivalTime() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": [
				      {"trnNo":"A0906","stnCd":"110","accomArrvDttm":"20260813090600","accomDptrDttm":"20260813090700","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0859","stnCd":"110","accomArrvDttm":"20260813085959","accomDptrDttm":"20260813090059","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0902","stnCd":"110","accomArrvDttm":"20260813090200","accomDptrDttm":"20260813090300","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0900","stnCd":"110","accomArrvDttm":"20260813090000","accomDptrDttm":"20260813090100","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0905","stnCd":"110","accomArrvDttm":"20260813090500","accomDptrDttm":"20260813090600","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0901","stnCd":"110","accomArrvDttm":"20260813090100","accomDptrDttm":"20260813090200","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0904","stnCd":"110","accomArrvDttm":"20260813090400","accomDptrDttm":"20260813090500","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0903","stnCd":"110","accomArrvDttm":"20260813090300","accomDptrDttm":"20260813090400","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0907","stnCd":"110","accomArrvDttm":"20260813090700","accomDptrDttm":"20260813090800","trnClsfNm":"LOCAL"}
				    ]}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(List.of("A0900", "A0901", "A0902", "A0903", "A0904"), snapshot.items().stream()
				.map(RailroadOperationItem::trainNumber)
				.toList());
	}

	@Test
	void mappersTreatNullItemsAsEmpty() throws Exception {
		ArrivalStatusApiResponse arrivalResponse = objectMapper.readValue("""
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{}}}
				""", ArrivalStatusApiResponse.class);
		PassengerForecastApiResponse passengerResponse = objectMapper.readValue("""
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{}}}
				""", PassengerForecastApiResponse.class);
		RailroadOperationApiResponse railroadResponse = objectMapper.readValue("""
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{}}}
				""", RailroadOperationApiResponse.class);

		assertEquals(List.of(), new ArrivalStatusMapper().map(arrivalResponse, collectedAt).items());
		assertEquals(List.of(), new PassengerForecastMapper().map(passengerResponse, collectedAt).items());
		assertEquals(List.of(), new RailroadOperationMapper().map(railroadResponse, collectedAt).items());
	}

	@Test
	void mapperRejectsPublicApiFailureResultCode() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR."},"body":{"items":[]}}}
				""", ArrivalStatusApiResponse.class);

		assertThrows(AirportApiResponseException.class, () -> new ArrivalStatusMapper().map(response, collectedAt));
	}
}
