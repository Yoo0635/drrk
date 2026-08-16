package com.drrk.messaging.congestion;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * v2 혼잡도 계약 (schemaVersion 5.0).
 *
 * <p>score = min(1, (currentLoad + forecastLoad) / capacity) — 다음 열차 도착 시점까지
 * 승강장에 누적될 철도행 수하물량(실측층 currentLoad + 예보층 forecastLoad)을 열차
 * 수용량으로 나눈 값. projectedScore는 score와 동일하다(하위 호환 유지용).</p>
 */
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

	public static final String SCHEMA_VERSION = "5.0";
	public static final String CALCULATION_VERSION_V2 = "platform-congestion-v2";

	public CongestionCalculatedMessage {
		Objects.requireNonNull(messageId, "messageId");
		Objects.requireNonNull(schemaVersion, "schemaVersion");
		Objects.requireNonNull(calculatedAt, "calculatedAt");
		Objects.requireNonNull(calculationVersion, "calculationVersion");
		Objects.requireNonNull(status, "status");
		Objects.requireNonNull(inputs, "inputs");
		railroadArrivals = railroadArrivals == null ? List.of() : List.copyOf(railroadArrivals);
		if (hasScore(status)) {
			requireFiniteNonNegative(score, "score");
			requireFiniteNonNegative(currentLoad, "currentLoad");
			requireFiniteNonNegative(forecastLoad, "forecastLoad");
			requireFiniteNonNegative(projectedScore, "projectedScore");
			if (capacity == null || capacity <= 0) {
				throw new IllegalArgumentException("capacity must be positive");
			}
			Objects.requireNonNull(lastTrainDepartureAt, "lastTrainDepartureAt");
			double expectedScore = clamp((currentLoad + forecastLoad) / capacity);
			if (Math.abs(score - expectedScore) > 1.0e-9) {
				throw new IllegalArgumentException(
						"score must equal min(1, (currentLoad + forecastLoad) / capacity)");
			}
			if (Math.abs(projectedScore - expectedScore) > 1.0e-9) {
				throw new IllegalArgumentException("projectedScore must equal score");
			}
			if (!Objects.equals(level, levelFor(score))) {
				throw new IllegalArgumentException("level must match score");
			}
		}
	}

	public static boolean hasScore(CongestionCalculationStatus status) {
		return status == CongestionCalculationStatus.CALCULATED
				|| status == CongestionCalculationStatus.NO_FLIGHT_DATA;
	}

	public static CongestionCalculatedMessage formulaPending(
			UUID messageId,
			Instant calculatedAt,
			CongestionInputReferences inputs
	) {
		return withoutScore(messageId, calculatedAt, "formula-pending-v1",
				CongestionCalculationStatus.FORMULA_PENDING, inputs);
	}

	/**
	 * 열차 미운행 시간대(막차 이후~첫차 이전 등 T_prev/T_next 미정의) — 혼잡도 미산출.
	 */
	public static CongestionCalculatedMessage noService(
			UUID messageId,
			Instant calculatedAt,
			CongestionInputReferences inputs
	) {
		return withoutScore(messageId, calculatedAt, CALCULATION_VERSION_V2,
				CongestionCalculationStatus.NO_SERVICE, inputs);
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
		return withScore(CongestionCalculationStatus.CALCULATED, messageId, calculatedAt, calculationVersion,
				sensorDetected, currentLoad, capacity, forecastLoad, lastTrainDepartureAt, railroadArrivals, inputs);
	}

	/**
	 * 예보층이 필요하지만 사용 가능한 항공편 데이터가 없는 경우 — 실측층만으로 산출.
	 */
	public static CongestionCalculatedMessage noFlightData(
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
		return withScore(CongestionCalculationStatus.NO_FLIGHT_DATA, messageId, calculatedAt, calculationVersion,
				sensorDetected, currentLoad, capacity, forecastLoad, lastTrainDepartureAt, railroadArrivals, inputs);
	}

	private static CongestionCalculatedMessage withoutScore(
			UUID messageId,
			Instant calculatedAt,
			String calculationVersion,
			CongestionCalculationStatus status,
			CongestionInputReferences inputs
	) {
		return new CongestionCalculatedMessage(
				messageId.toString(),
				SCHEMA_VERSION,
				calculatedAt,
				calculationVersion,
				status,
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

	private static CongestionCalculatedMessage withScore(
			CongestionCalculationStatus status,
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
		double score = clamp((currentLoad + forecastLoad) / capacity);
		return new CongestionCalculatedMessage(
				messageId.toString(),
				SCHEMA_VERSION,
				calculatedAt,
				calculationVersion,
				status,
				sensorDetected,
				score,
				levelFor(score),
				inputs,
				currentLoad,
				capacity,
				forecastLoad,
				score,
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
