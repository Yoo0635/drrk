package com.drrk.collector.congestion;

import com.drrk.messaging.congestion.AirportRoute;
import java.util.Objects;

public record MovingWalkwayRouteDefinition(
		AirportRoute route,
		double split,
		long retentionLengthSeconds,
		long walkwayArrivalOffsetSeconds,
		long availablePassageTimeSeconds,
		long congestedPassageTimeSeconds,
		long remainingTravelTimeSeconds
) {

	public MovingWalkwayRouteDefinition {
		Objects.requireNonNull(route, "route");
		if (!Double.isFinite(split) || split < 0 || split > 1) {
			throw new IllegalArgumentException("split must be between 0 and 1");
		}
		if (retentionLengthSeconds <= 0
				|| walkwayArrivalOffsetSeconds < 0
				|| availablePassageTimeSeconds < 0
				|| congestedPassageTimeSeconds < 0
				|| remainingTravelTimeSeconds < 0) {
			throw new IllegalArgumentException("Route times must be non-negative and retention length must be positive");
		}
	}
}
