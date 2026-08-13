package com.drrk.main.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import com.drrk.main.service.AirportGuideService;
import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionInputReferences;
import com.drrk.messaging.congestion.MovingWalkwayStatus;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import com.drrk.messaging.congestion.RailroadArrivalStatus;
import com.drrk.messaging.congestion.RouteCongestionResult;
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
		mockMvc.perform(get("/api/v1/routes/recommendation"))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/v1/airport-railroad/arrivals"))
				.andExpect(status().isNoContent());
	}

	@Test
	void returnsRecommendationAndRouteArrivalCongestion() throws Exception {
		store.handle(calculatedMessage());

		mockMvc.perform(get("/api/v1/routes/recommendation"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendedRoute").value("B"))
				.andExpect(jsonPath("$.routes[0].route").value("B"))
				.andExpect(jsonPath("$.routes[0].congestionStatus").value("AVAILABLE"))
				.andExpect(jsonPath("$.routes[0].totalTravelTimeSeconds").value(80));
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
		RouteCongestionResult routeB = new RouteCongestionResult(
				AirportRoute.B,
				Instant.parse("2026-08-13T05:00:30Z"),
				1,
				1,
				1,
				3,
				3 / 4.2,
				MovingWalkwayStatus.AVAILABLE,
				20,
				80
		);
		RouteCongestionResult routeC = new RouteCongestionResult(
				AirportRoute.C,
				Instant.parse("2026-08-13T05:00:40Z"),
				2,
				2,
				2,
				6,
				6 / 4.2,
				MovingWalkwayStatus.CONGESTED,
				40,
				100
		);
		return CongestionCalculatedMessage.calculated(
				UUID.fromString("35c9ef91-9f68-4fda-833f-90fa54c25816"),
				Instant.parse("2026-08-13T05:00:00Z"),
				"moving-walkway-v1",
				List.of(routeB, routeC),
				AirportRoute.B,
				List.of(new RailroadArrivalResult("1234", "일반", "14:55", null, RailroadArrivalStatus.SCHEDULED)),
				new CongestionInputReferences(
						Instant.EPOCH,
						1,
						Instant.EPOCH,
						1,
						Instant.EPOCH,
						1,
						"8c530c6c-f819-4ad6-b687-760dc698c617",
						Instant.EPOCH
				)
		);
	}
}
