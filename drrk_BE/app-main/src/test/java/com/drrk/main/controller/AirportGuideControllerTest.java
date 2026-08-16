package com.drrk.main.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import com.drrk.main.service.AirportGuideService;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

class AirportGuideControllerTest {

	private LatestAirportGuideStore store;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		store = new LatestAirportGuideStore();
		mockMvc = MockMvcBuilders.standaloneSetup(
				new AirportGuideController(new AirportGuideService(store))
		).build();
	}

	@Test
	void returnsNoContentBeforeFirstCalculatedGuideArrives() throws Exception {
		mockMvc.perform(get("/api/v1/platform/congestion"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/airport-railroad/arrivals"))
				.andExpect(status().isNoContent());
	}

	@Test
	void returnsPlatformCongestionSummary() throws Exception {
		store.handle(calculatedMessage());

		mockMvc.perform(get("/api/v1/platform/congestion"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.score").value(0.6770833333333334))
				.andExpect(jsonPath("$.level").value("MEDIUM"))
				.andExpect(jsonPath("$.currentLoad").value(24.0))
				.andExpect(jsonPath("$.capacity").value(48))
				.andExpect(jsonPath("$.forecastLoad").value(8.5))
				.andExpect(jsonPath("$.projectedScore").value(0.6770833333333334))
				.andExpect(jsonPath("$.sensorDetected").value(true))
				.andExpect(jsonPath("$.lastTrainDepartureAt").value("2026-08-13T05:55:00Z"))
				.andExpect(jsonPath("$.calculatedAt").value("2026-08-13T06:00:00Z"));
	}

	@Test
	void keepsCompatibilityAliasForOldRecommendationEndpoint() throws Exception {
		store.handle(calculatedMessage());

		mockMvc.perform(get("/api/v1/routes/recommendation"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.score").value(0.6770833333333334));
	}

	@Test
	void returnsRailroadShapeExpectedByFrontend() throws Exception {
		store.handle(calculatedMessage());

		mockMvc.perform(get("/api/v1/airport-railroad/arrivals"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$[0].trainNo").value("1234"))
				.andExpect(jsonPath("$[0].trainType").value("일반"))
				.andExpect(jsonPath("$[0].scheduledArrivalTime").value("14:55"))
				.andExpect(jsonPath("$[0].actualArrivalTime").value(nullValue()))
				.andExpect(jsonPath("$[0].status").value("SCHEDULED"));
	}

	private CongestionCalculatedMessage calculatedMessage() {
		return CongestionCalculatedMessage.calculated(
				UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816"),
				Instant.parse("2026-08-13T06:00:00Z"),
				"platform-congestion-v2",
				true,
				24.0,
				48L,
				8.5,
				Instant.parse("2026-08-13T05:55:00Z"),
				List.of(new RailroadArrivalResult("1234", "일반", "14:55", null, RailroadArrivalStatus.SCHEDULED)),
				inputs()
		);
	}

	private CongestionInputReferences inputs() {
		return new CongestionInputReferences(
				Instant.EPOCH,
				1,
				Instant.EPOCH,
				1,
				Instant.EPOCH,
				1,
				"8c530c6c-f819-4ad6-b687-760dc698c617",
				Instant.EPOCH
		);
	}
}
