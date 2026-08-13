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

	private static final double CAPACITY = 4.2;
	private static final double EPSILON = 1e-9;

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
		double expectedLoad = stay + incoming + residual;
		if (Math.abs(load - expectedLoad) > EPSILON) {
			throw new IllegalArgumentException(
					"load must equal stay + incoming + residual (expected " + expectedLoad + ", got " + load + ")"
			);
		}
		double expectedRatio = load / CAPACITY;
		if (Math.abs(volumeCapacityRatio - expectedRatio) > EPSILON) {
			throw new IllegalArgumentException(
					"volumeCapacityRatio must equal load / " + CAPACITY + " (expected " + expectedRatio + ", got " + volumeCapacityRatio + ")"
			);
		}
		MovingWalkwayStatus expectedStatus = MovingWalkwayStatus.fromVolumeCapacityRatio(volumeCapacityRatio);
		if (congestionStatus != expectedStatus) {
			throw new IllegalArgumentException(
					"congestionStatus must match volumeCapacityRatio (expected " + expectedStatus + " for ratio " + volumeCapacityRatio + ", got " + congestionStatus + ")"
			);
		}
	}

	private static void requireNonNegativeFinite(double value, String field) {
		if (!Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException(field + " must be finite and non-negative");
		}
	}
}
