package com.drrk.messaging.congestion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

public record CongestionCalculatedMessage(
		String messageId,
		String schemaVersion,
		Instant calculatedAt,
		String calculationVersion,
		CongestionCalculationStatus status,
		Double score,
		String level,
		CongestionInputReferences inputs,
		List<RouteCongestionResult> routeResults,
		AirportRoute recommendedRoute,
		List<RailroadArrivalResult> railroadArrivals
) {

	public CongestionCalculatedMessage {
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(schemaVersion, "schemaVersion");
		Objects.requireNonNull(calculatedAt, "calculatedAt");
		Objects.requireNonNull(calculationVersion, "calculationVersion");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(inputs, "inputs");
		routeResults = routeResults == null ? List.of() : List.copyOf(routeResults);
		railroadArrivals = railroadArrivals == null ? List.of() : List.copyOf(railroadArrivals);
		if (status == CongestionCalculationStatus.CALCULATED) {
			if (routeResults.isEmpty() || recommendedRoute == null
					|| routeResults.stream().noneMatch(result -> result.route() == recommendedRoute)) {
				throw new IllegalArgumentException("Calculated result must contain the recommended route");
			}
		}
	}

	public static CongestionCalculatedMessage formulaPending(
			UUID messageId,
			Instant calculatedAt,
			CongestionInputReferences inputs
	) {
		return new CongestionCalculatedMessage(
				messageId.toString(),
				"2.0",
				calculatedAt,
				"formula-pending-v0",
				CongestionCalculationStatus.FORMULA_PENDING,
				null,
				null,
				inputs,
				List.of(),
				null,
				List.of()
		);
	}

	public static CongestionCalculatedMessage calculated(
			UUID messageId,
			Instant calculatedAt,
			String calculationVersion,
			List<RouteCongestionResult> routeResults,
			AirportRoute recommendedRoute,
			List<RailroadArrivalResult> railroadArrivals,
			CongestionInputReferences inputs
	) {
		Objects.requireNonNull(routeResults, "routeResults");
		RouteCongestionResult recommended = routeResults.stream()
				.filter(result -> result.route() == recommendedRoute)
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Recommended route must be present in routeResults"));
		return new CongestionCalculatedMessage(
				messageId.toString(),
				"2.0",
				calculatedAt,
				calculationVersion,
				CongestionCalculationStatus.CALCULATED,
				recommended.volumeCapacityRatio(),
				recommended.congestionStatus().name(),
				inputs,
				routeResults,
				recommendedRoute,
				railroadArrivals
		);
	}
}
