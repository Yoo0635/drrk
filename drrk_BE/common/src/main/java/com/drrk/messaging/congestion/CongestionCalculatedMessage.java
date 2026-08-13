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
		boolean sensorDetected,
		Double score,
		String level,
		CongestionInputReferences inputs,
		Double currentLoad,
		Long capacity,
		Double forecastLoad,
		Double projectedScore,
		Instant lastTrainDepartureAt,
		List<RailroadArrivalResult> railroadArrivals
) {

	public CongestionCalculatedMessage {
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(schemaVersion, "schemaVersion");
		Objects.requireNonNull(calculatedAt, "calculatedAt");
		Objects.requireNonNull(calculationVersion, "calculationVersion");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(inputs, "inputs");
		railroadArrivals = railroadArrivals == null ? List.of() : List.copyOf(railroadArrivals);
		if (status == CongestionCalculationStatus.CALCULATED) {
			requireFiniteNonNegative(score, "score");
			requireFiniteNonNegative(currentLoad, "currentLoad");
			requireFiniteNonNegative(forecastLoad, "forecastLoad");
			requireFiniteNonNegative(projectedScore, "projectedScore");
			if (capacity == null || capacity <= 0) {
				throw new IllegalArgumentException("capacity must be positive");
			}
			Objects.requireNonNull(lastTrainDepartureAt, "lastTrainDepartureAt");
			double expectedScore = clamp(currentLoad / capacity);
			double expectedProjectedScore = clamp((currentLoad + forecastLoad) / capacity);
			if (Math.abs(score - expectedScore) > 1.0e-9) {
				throw new IllegalArgumentException("score must equal min(1, currentLoad / capacity)");
			}
			if (Math.abs(projectedScore - expectedProjectedScore) > 1.0e-9) {
				throw new IllegalArgumentException("projectedScore must equal min(1, (currentLoad + forecastLoad) / capacity)");
			}
			if (!Objects.equals(level, levelFor(score))) {
				throw new IllegalArgumentException("level must match score");
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
				"4.0",
				calculatedAt,
				"formula-pending-v1",
				CongestionCalculationStatus.FORMULA_PENDING,
				false,
				null,
				null,
				inputs,
				null,
				null,
				null,
				null,
				null,
				List.of()
		);
	}

	public static CongestionCalculatedMessage calculated(
			UUID messageId,
			Instant calculatedAt,
			String calculationVersion,
			boolean sensorDetected,
			double currentLoad,
			long capacity,
			double forecastLoad,
			Instant lastTrainDepartureAt,
			List<RailroadArrivalResult> railroadArrivals,
			CongestionInputReferences inputs
	) {
		if (capacity <= 0) {
			throw new IllegalArgumentException("capacity must be positive");
		}
		if (lastTrainDepartureAt == null) {
			throw new IllegalArgumentException("lastTrainDepartureAt must not be null");
		}
		double score = clamp(currentLoad / capacity);
		double projectedScore = clamp((currentLoad + forecastLoad) / capacity);
		return new CongestionCalculatedMessage(
				messageId.toString(),
				"4.0",
				calculatedAt,
				calculationVersion,
				CongestionCalculationStatus.CALCULATED,
				sensorDetected,
				score,
				levelFor(score),
				inputs,
				currentLoad,
				capacity,
				forecastLoad,
				projectedScore,
				lastTrainDepartureAt,
				railroadArrivals
		);
	}

	private static void requireFiniteNonNegative(Double value, String field) {
		if (value == null || !Double.isFinite(value) || value < 0) {
			throw new IllegalArgumentException(field + " must be finite and non-negative");
		}
	}

	private static double clamp(double value) {
		return Math.min(1d, value);
	}

	private static String levelFor(double score) {
		if (score >= 1d) {
			return "FULL";
		}
		if (score >= 0.7d) {
			return "HIGH";
		}
		if (score >= 0.4d) {
			return "MEDIUM";
		}
		return "LOW";
	}
}
