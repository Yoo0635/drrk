package com.drrk.messaging.congestion;

import java.time.Instant;
import java.util.Objects;

public record RouteCongestionResult(
		AirportRoute route,
		Instant walkwayArrivalTime,
		double stay,
		double incoming,
		double residual,
		double load,
		double volumeCapacityRatio,
		MovingWalkwayStatus congestionStatus,
		long passageTimeSeconds,
		long totalTravelTimeSeconds
) {

	public RouteCongestionResult {
		Objects.requireNonNull(route, "route");
		Objects.requireNonNull(walkwayArrivalTime, "walkwayArrivalTime");
		Objects.requireNonNull(congestionStatus, "congestionStatus");
		requireNonNegativeFinite(stay, "stay");
		requireNonNegativeFinite(incoming, "incoming");
		requireNonNegativeFinite(residual, "residual");
		requireNonNegativeFinite(load, "load");
		requireNonNegativeFinite(volumeCapacityRatio, "volumeCapacityRatio");
		if (passageTimeSeconds < 0 || totalTravelTimeSeconds < passageTimeSeconds) {
			throw new IllegalArgumentException("Travel times must be non-negative and total must include passage time");
		}
	}

	private static void requireNonNegativeFinite(double value, String field) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException(field + " must be finite and non-negative");
		}
	}
}
