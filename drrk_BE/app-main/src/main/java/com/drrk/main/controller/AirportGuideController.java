package com.drrk.main.controller;

import com.drrk.main.service.AirportGuideService;
import com.drrk.main.service.RouteRecommendationResponse;
import com.drrk.messaging.congestion.RailroadArrivalResult;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
public class AirportGuideController {

	private final AirportGuideService service;

	public AirportGuideController(AirportGuideService service) {
		this.service = service;
	}

	@GetMapping("/routes/recommendation")
	public ResponseEntity<RouteRecommendationResponse> routeRecommendation() {
		return service.routeRecommendation()
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}

	@GetMapping("/airport-railroad/arrivals")
	public ResponseEntity<List<RailroadArrivalResult>> railroadArrivals() {
		return service.railroadArrivals()
				.map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
