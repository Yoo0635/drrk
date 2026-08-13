package com.drrk.main.service;

import com.drrk.messaging.congestion.AirportRoute;
import com.drrk.messaging.congestion.RouteCongestionResult;
import java.time.Instant;
import java.util.List;

public record RouteRecommendationResponse(
		AirportRoute recommendedRoute,
		Instant calculatedAt,
		boolean sensorDetected,
		List<RouteCongestionResult> routes
) {

	public RouteRecommendationResponse {
		routes = List.copyOf(routes);
	}
}
