package com.drrk.main.service;

import com.drrk.main.consumer.congestion.LatestAirportGuideStore;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Service;

@Service
public class AirportGuideService {

	private final LatestAirportGuideStore store;

	public AirportGuideService(LatestAirportGuideStore store) {
		this.store = store;
	}

	public Optional<RouteRecommendationResponse> routeRecommendation() {
		return store.latest().map(message -> new RouteRecommendationResponse(
				message.recommendedRoute(),
				message.calculatedAt(),
				message.sensorDetected(),
				message.routeResults()
		));
	}

	public Optional<List<RailroadArrivalResult>> railroadArrivals() {
		return store.latest().map(message -> List.copyOf(message.railroadArrivals()));
	}
}
