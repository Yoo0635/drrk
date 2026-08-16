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
				    "body": {"items": {"item": [
				      {"terno": "T1", "entrygate": " b ", "flightid": "KE001", "estimatedtime": "202608131200", "scheduletime": "202608131159", "korean": "10", "foreigner": "4"},
				      {"terno": "t1", "entrygate": " c ", "flightid": "OZ002", "estimatedtime": "", "scheduletime": "202608131205", "korean": "0", "foreigner": "7"},
				      {"terno": "T1", "entrygate": "A", "flightid": "KE002", "estimatedtime": "202608131205"},
				      {"terno": "T2", "entrygate": "B", "flightid": "KE003", "estimatedtime": "202608131210"},
				      {"terno": "T1", "entrygate": "B", "flightid": "KE004", "estimatedtime": "", "scheduletime": "", "korean": "1", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "B", "flightid": "KE005", "estimatedtime": "202608131215", "korean": "10.0", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "C", "flightid": "KE006", "estimatedtime": "202608131220", "korean": "-1", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "C", "flightid": "KE007", "estimatedtime": "not-a-time", "korean": "1", "foreigner": "1"},
				      {"terno": "T1", "entrygate": "C", "flightid": "", "estimatedtime": "202608131225", "korean": "1", "foreigner": "1"}
				    ]}, "totalCount": 9}
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
	void arrivalMapperKeepsRecentAndUpcomingFlightsOrderedByEstimatedTime() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"terno":"T1","entrygate":"B","flightid":"KE0906","estimatedtime":"202608130906","korean":"6","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0859","estimatedtime":"202608130859","korean":"59","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0902","estimatedtime":"202608130902","korean":"2","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0900","estimatedtime":"202608130900","korean":"0","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0905","estimatedtime":"202608130905","korean":"5","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0901","estimatedtime":"202608130901","korean":"1","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0904","estimatedtime":"202608130904","korean":"4","foreigner":"0"},
				      {"terno":"T1","entrygate":"C","flightid":"KE0903","estimatedtime":"202608130903","korean":"3","foreigner":"0"},
				      {"terno":"T1","entrygate":"B","flightid":"KE0907","estimatedtime":"202608130907","korean":"7","foreigner":"0"}
				    ]}}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		// v2 예보층은 이미 착륙한 편(KE0859)도 필요하므로 과거 도착편까지 시간순으로 남긴다
		assertEquals(
				List.of("KE0859", "KE0900", "KE0901", "KE0902", "KE0903", "KE0904", "KE0905", "KE0906", "KE0907"),
				snapshot.items().stream().map(ArrivalStatusItem::flightId).toList()
		);
	}

	@Test
	void passengerMapperAcceptsRealResponseWithoutResponseWrapper() throws Exception {
		// 승객예고 API는 다른 두 API와 달리 response 래퍼 없이 header/body를 최상위로 내려준다.
		PassengerForecastApiResponse response = objectMapper.readValue("""
				{
				  "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				  "body": {
				    "items": {"item": [
				      {"adate": "20260812", "atime": "14_15", "t1eg1": "120", "t1eg2": "85", "t1eg3": "60",
				       "t1eg4": "75", "t1egsum1": "340", "t2eg1": "90", "t2eg2": "110", "t2egsum1": "200"}
				    ]},
				    "numOfRows": "24", "pageNo": "1", "totalCount": "24"
				  }
				}
				""", PassengerForecastApiResponse.class);

		PassengerForecastSnapshot snapshot = new PassengerForecastMapper().map(response, collectedAt);

		assertEquals(List.of(new PassengerForecastItem("20260812", "14_15", 120)), snapshot.items());
	}

	@Test
	void passengerMapperFailsLoudlyWhenHeaderIsMissing() throws Exception {
		PassengerForecastApiResponse response = objectMapper.readValue(
				"{\"body\": {\"items\": {\"item\": []}}}", PassengerForecastApiResponse.class);

		assertThrows(AirportApiResponseException.class,
				() -> new PassengerForecastMapper().map(response, collectedAt));
	}

	@Test
	void arrivalMapperAcceptsRealResponseWithDecimalPassengerCounts() throws Exception {
		// 입국장현황 API는 승객 수를 "120.0" 처럼 소수점 문자열로 내려준다.
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"airport": "FRA", "flightid": "OZ542", "terno": "T1", "entrygate": "B", "gatenumber": "15",
				       "scheduletime": "202608130815", "estimatedtime": "202608130834",
				       "korean": "120.0", "foreigner": "46.0"}
				    ]}, "numOfRows": "10", "pageNo": "1", "totalCount": "35"}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		assertEquals(
				List.of(new ArrivalStatusItem("B", "OZ542", "202608130834", 120, 46)),
				snapshot.items()
		);
	}

	@Test
	void arrivalMapperFallsBackToScheduleTimeWhenEstimatedTimeIsBlank() throws Exception {
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"terno": "T1", "entrygate": "C", "flightid": "KE100", "estimatedtime": "",
				       "scheduletime": "202608130840", "korean": "30.0", "foreigner": "10.0"}
				    ]}}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		assertEquals(
				List.of(new ArrivalStatusItem("C", "KE100", "202608130840", 30, 10)),
				snapshot.items()
		);
	}

	@Test
	void arrivalMapperKeepsAlreadyLandedFlightsForForecastLayer() throws Exception {
		// 착륙 후 45~90분 뒤 출구를 통과하므로, 이미 도착한 편이 예보층의 핵심 입력이다.
		ArrivalStatusApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"terno":"T1","entrygate":"B","flightid":"PAST","estimatedtime":"202608130800","korean":"50.0","foreigner":"20.0"},
				      {"terno":"T1","entrygate":"B","flightid":"TOO_OLD","estimatedtime":"202608130400","korean":"50.0","foreigner":"20.0"}
				    ]}}
				  }
				}
				""", ArrivalStatusApiResponse.class);

		// collectedAt = 2026-08-13T00:00:00Z = 09:00 KST → PAST(08:00 KST)는 1시간 전, TOO_OLD(04:00 KST)는 5시간 전
		ArrivalStatusSnapshot snapshot = new ArrivalStatusMapper().map(response, collectedAt);

		assertEquals(List.of("PAST"), snapshot.items().stream().map(ArrivalStatusItem::flightId).toList());
	}

	@Test
	void passengerMapperUsesTerminalOneEntryGateOneExactCounts() throws Exception {
		PassengerForecastApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"adate": "20260813", "atime": "00_01", "t1eg1": "722", "t1egsum1": "999.0"},
				      {"adate": "20260813", "atime": "01_02", "t1eg1": "722.0"},
				      {"adate": "20260813", "atime": "02_03", "t1eg1": "-1"}
				    ]}}
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
				    "body": {"items": {"item": [
				      {"trnNo": "A2002", "stnCd": " 110 ", "accomArrvDttm": "20260813090900", "planDptrDttm": "20260813091000", "accomDptrDttm": "20260813091100", "trnClsfNm": "AREX"},
				      {"trnNo": "A2003", "stnCd": "111", "accomArrvDttm": "20260813100800", "planDptrDttm": "20260813100900", "accomDptrDttm": "20260813101000", "trnClsNm": "LOCAL"},
				      {"trnNo": "A2004", "stnCd": "112", "accomArrvDttm": "", "planDptrDttm": "20260813110900", "accomDptrDttm": "20260813111000", "trnClsfNm": "LOCAL"}
				    ]}}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(
				List.of(
						new RailroadOperationItem(
								"A2002", "110", "20260813090900", null, "20260813091000", "20260813091100", "AREX"
						),
						new RailroadOperationItem(
								"A2003", "111", "20260813100800", null, "20260813100900", "20260813101000", "LOCAL"
						)
				),
				snapshot.items()
		);
	}

	@Test
	void railroadMapperFallsBackToPlannedTimesWhenActualTimesAreBlank() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"trnNo": "A2135", "stnCd": "077", "planArrvDttm": "20260814154700", "planDptrDttm": "20260814154730", "accomArrvDttm": "", "accomDptrDttm": "", "trnClsfNm": "Comm"}
				    ]}}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(
				List.of(
						new RailroadOperationItem(
								"A2135", "077", "20260814154700", null, "20260814154730", "20260814154730", "Comm"
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
				    "body": {"items": {"item": [
				      {"trnNo":"A0906","stnCd":"110","accomArrvDttm":"20260813090600","planDptrDttm":"20260813090700","accomDptrDttm":"20260813090730","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0859","stnCd":"110","accomArrvDttm":"20260813085959","planDptrDttm":"20260813090059","accomDptrDttm":"20260813090129","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0902","stnCd":"110","accomArrvDttm":"20260813090200","planDptrDttm":"20260813090300","accomDptrDttm":"20260813090330","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0900","stnCd":"110","accomArrvDttm":"20260813090000","planDptrDttm":"20260813090100","accomDptrDttm":"20260813090130","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0905","stnCd":"110","accomArrvDttm":"20260813090500","planDptrDttm":"20260813090600","accomDptrDttm":"20260813090630","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0901","stnCd":"110","accomArrvDttm":"20260813090100","planDptrDttm":"20260813090200","accomDptrDttm":"20260813090230","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0904","stnCd":"110","accomArrvDttm":"20260813090400","planDptrDttm":"20260813090500","accomDptrDttm":"20260813090530","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0903","stnCd":"110","accomArrvDttm":"20260813090300","planDptrDttm":"20260813090400","accomDptrDttm":"20260813090430","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0907","stnCd":"110","accomArrvDttm":"20260813090700","planDptrDttm":"20260813090800","accomDptrDttm":"20260813090830","trnClsfNm":"LOCAL"}
				    ]}}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(List.of("A0859", "A0900", "A0901", "A0902", "A0903"), snapshot.items().stream()
				.map(RailroadOperationItem::trainNumber)
				.toList());
	}

	@Test
	void railroadMapperKeepsLatestPastTrainForCongestionBaseline() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
				    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
				    "body": {"items": {"item": [
				      {"trnNo":"A0858","stnCd":"110","accomArrvDttm":"20260813085800","planDptrDttm":"20260813085900","accomDptrDttm":"20260813085930","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0859","stnCd":"110","accomArrvDttm":"20260813085930","planDptrDttm":"20260813090030","accomDptrDttm":"20260813090100","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0900","stnCd":"110","accomArrvDttm":"20260813090000","planDptrDttm":"20260813090100","accomDptrDttm":"20260813090130","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0901","stnCd":"110","accomArrvDttm":"20260813090100","planDptrDttm":"20260813090200","accomDptrDttm":"20260813090230","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0902","stnCd":"110","accomArrvDttm":"20260813090200","planDptrDttm":"20260813090300","accomDptrDttm":"20260813090330","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0903","stnCd":"110","accomArrvDttm":"20260813090300","planDptrDttm":"20260813090400","accomDptrDttm":"20260813090430","trnClsfNm":"LOCAL"},
				      {"trnNo":"A0904","stnCd":"110","accomArrvDttm":"20260813090400","planDptrDttm":"20260813090500","accomDptrDttm":"20260813090530","trnClsfNm":"LOCAL"}
				    ]}}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(List.of("A0859", "A0900", "A0901", "A0902", "A0903"), snapshot.items().stream()
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
					{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE KEY IS NOT REGISTERED ERROR."},"body":{"items":{"item":[]}}}}
					""", ArrivalStatusApiResponse.class);

		assertThrows(AirportApiResponseException.class, () -> new ArrivalStatusMapper().map(response, collectedAt));
	}

	@Test
	void railroadMapperRejectsBlankTrainNumber() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
					    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
					    "body": {"items": {"item": [
					      {"trnNo": "", "stnCd": "110", "accomArrvDttm": "20260813090000", "planDptrDttm": "20260813090100", "accomDptrDttm": "20260813090130", "trnClsfNm": "LOCAL"},
					      {"trnNo": "   ", "stnCd": "111", "accomArrvDttm": "20260813090100", "planDptrDttm": "20260813090200", "accomDptrDttm": "20260813090230", "trnClsfNm": "LOCAL"},
					      {"trnNo": "A0900", "stnCd": "110", "accomArrvDttm": "20260813090200", "planDptrDttm": "20260813090300", "accomDptrDttm": "20260813090330", "trnClsfNm": "LOCAL"}
					    ]}}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(List.of("A0900"), snapshot.items().stream()
				.map(RailroadOperationItem::trainNumber)
				.toList());
	}

	@Test
	void railroadMapperRejectsBlankStationCode() throws Exception {
		RailroadOperationApiResponse response = objectMapper.readValue("""
				{
				  "response": {
					    "header": {"resultCode": "00", "resultMsg": "NORMAL SERVICE."},
					    "body": {"items": {"item": [
					      {"trnNo": "A0900", "stnCd": "", "accomArrvDttm": "20260813090000", "planDptrDttm": "20260813090100", "accomDptrDttm": "20260813090130", "trnClsfNm": "LOCAL"},
					      {"trnNo": "A0901", "stnCd": "   ", "accomArrvDttm": "20260813090100", "planDptrDttm": "20260813090200", "accomDptrDttm": "20260813090230", "trnClsfNm": "LOCAL"},
					      {"trnNo": "A0902", "stnCd": "110", "accomArrvDttm": "20260813090200", "planDptrDttm": "20260813090300", "accomDptrDttm": "20260813090330", "trnClsfNm": "LOCAL"}
					    ]}}
				  }
				}
				""", RailroadOperationApiResponse.class);

		RailroadOperationSnapshot snapshot = new RailroadOperationMapper().map(response, collectedAt);

		assertEquals(List.of("A0902"), snapshot.items().stream()
				.map(RailroadOperationItem::trainNumber)
				.toList());
	}
}
