package com.drrk.main.service;

import java.time.Instant;

public record PlatformCongestionResponse(
		Instant calculatedAt,
		double score,
		String level,
		double currentLoad,
		long capacity,
		double forecastLoad,
		double projectedScore,
		boolean sensorDetected,
		Instant lastTrainDepartureAt
) {
}
