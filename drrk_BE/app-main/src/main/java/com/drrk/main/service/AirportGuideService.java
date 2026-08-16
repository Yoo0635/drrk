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

	public Optional<PlatformCongestionResponse> platformCongestion() {
		return store.latest().map(message -> new PlatformCongestionResponse(
				message.calculatedAt(),
				message.score(),
				message.level(),
				message.currentLoad(),
				message.capacity(),
				message.forecastLoad(),
				message.projectedScore(),
				message.sensorDetected(),
				message.lastTrainDepartureAt()
		));
	}

	public Optional<List<RailroadArrivalResult>> railroadArrivals() {
		return store.latest().map(message -> List.copyOf(message.railroadArrivals()));
	}
}
