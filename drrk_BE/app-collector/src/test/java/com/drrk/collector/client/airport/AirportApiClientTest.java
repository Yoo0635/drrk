package com.drrk.collector.client.airport;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class AirportApiClientTest {

	private final Instant now = Instant.parse("2026-08-13T00:00:00Z");
	private final Clock clock = Clock.fixed(now, ZoneOffset.UTC);

	@Test
	void arrivalClientRequestsTerminalOneAsJson() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://airport.test/arrival/getArrivalsCongestion?pageNo=1&numOfRows=1000&type=json&terno=T1&serviceKey=arrival-key"))
				.andExpect(method(HttpMethod.GET))
				.andRespond(withSuccess(arrivalJson(), MediaType.APPLICATION_JSON));
		ArrivalStatusClient client = new ArrivalStatusClient(
				builder,
				properties(),
				new ArrivalStatusMapper(),
				clock
		);

		assertEquals(1, client.fetch().items().size());
		server.verify();
	}

	@Test
	void passengerClientRequestsTodayForecast() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://airport.test/passenger/getPassgrAnncmt?pageNo=1&numOfRows=1000&type=json&selectdate=0&serviceKey=passenger-key"))
				.andRespond(withSuccess(passengerJson(), MediaType.APPLICATION_JSON));
		PassengerForecastClient client = new PassengerForecastClient(
				builder,
				properties(),
				new PassengerForecastMapper(),
				clock
		);

		assertEquals(722, client.fetch().items().getFirst().expectedPassengerCount());
		server.verify();
	}

	@Test
	void railroadClientRequestsSeoulDateAndConfiguredFilters() {
		RestClient.Builder builder = RestClient.builder();
		MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
		server.expect(requestTo("https://airport.test/railroad/getAirportRailroad?pageNo=1&numOfRows=1000&type=json&drvDt=20260813&trainClsf=Comm&stnCd=110&serviceKey=railroad-key"))
				.andExpect(queryParam("serviceKey", "railroad-key"))
				.andRespond(withSuccess(railroadJson(), MediaType.APPLICATION_JSON));
		RailroadOperationClient client = new RailroadOperationClient(
				builder,
				properties(),
				new RailroadOperationMapper(),
				clock
		);

		assertEquals("A2002", client.fetch().items().getFirst().trainNumber());
		server.verify();
	}

	private AirportApiProperties properties() {
		return new AirportApiProperties(
				1000,
				new AirportApiProperties.Source("https://airport.test/arrival", "arrival-key"),
				new AirportApiProperties.Source("https://airport.test/passenger", "passenger-key"),
				new AirportApiProperties.RailroadSource(
						"https://airport.test/railroad",
						"railroad-key",
						"Comm",
						"110"
				)
		);
	}

	private String arrivalJson() {
		return """
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[{"terno":"T1","entrygate":"B","flightid":"KE001","estimatedtime":"202608131200","korean":"10","foreigner":"4"}]}}}}
				""";
	}

	private String passengerJson() {
		return """
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[{"adate":"20260813","atime":"00_01","t1eg1":"722"}]}}}}
				""";
	}

	private String railroadJson() {
		return """
				{"response":{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},"body":{"items":{"item":[{"trnNo":"A2002","stnCd":"110","accomArrvDttm":"20260813090800","accomDptrDttm":"20260813090900","trnClsfNm":"AREX"}]}}}}
				""";
	}
}
