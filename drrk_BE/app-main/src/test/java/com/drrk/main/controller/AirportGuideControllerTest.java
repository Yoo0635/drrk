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
import com.drrk.messaging.congestion.RouteStatus;
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
	void returnsDetectedSensorRecommendationWithAllRouteStatusesAndTimes() throws Exception {
		store.handle(calculatedMessage());

		mockMvc.perform(get("/api/v1/routes/recommendation"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.recommendedRoute").value("C"))
				.andExpect(jsonPath("$.sensorDetected").value(true))
				.andExpect(jsonPath("$.calculatedAt").value("2026-08-13T05:00:00Z"))
				.andExpect(jsonPath("$.routes[0].route").value("A"))
				.andExpect(jsonPath("$.routes[0].status").value("CLEAR"))
				.andExpect(jsonPath("$.routes[0].totalTravelTimeSeconds").value(509))
				.andExpect(jsonPath("$.routes[1].route").value("B"))
				.andExpect(jsonPath("$.routes[1].status").value("CONGESTED"))
				.andExpect(jsonPath("$.routes[1].totalTravelTimeSeconds").value(480))
				.andExpect(jsonPath("$.routes[2].route").value("C"))
				.andExpect(jsonPath("$.routes[2].status").value("CLEAR"))
				.andExpect(jsonPath("$.routes[2].totalTravelTimeSeconds").value(434));
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
				Instant.parse("2026-08-13T05:00:00Z"),
				"moving-walkway-v1",
				true,
				List.of(
						route(AirportRoute.A, 3, MovingWalkwayStatus.NORMAL, RouteStatus.CLEAR, 422, 509),
						route(AirportRoute.B, 3, MovingWalkwayStatus.NORMAL, RouteStatus.CONGESTED, 110, 480),
						route(AirportRoute.C, 6, MovingWalkwayStatus.CONGESTED, RouteStatus.CLEAR, 347, 434)
				),
				AirportRoute.C,
				List.of(new RailroadArrivalResult("1234", "일반", "14:55", null, RailroadArrivalStatus.SCHEDULED)),
				inputs()
		);
	}

	private RouteCongestionResult route(
			AirportRoute route,
			double load,
			MovingWalkwayStatus congestionStatus,
			RouteStatus status,
			long passageTimeSeconds,
			long totalTravelTimeSeconds
	) {
		return new RouteCongestionResult(
				route,
				Instant.parse("2026-08-13T05:00:30Z"),
				load / 3,
				load / 3,
				load / 3,
				load,
				load / 4.2,
				congestionStatus,
				status,
				passageTimeSeconds,
				totalTravelTimeSeconds
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
