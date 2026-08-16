package com.drrk.messaging.congestion;

import java.time.Instant;
import java.util.Objects;

public record CongestionInputReferences(
		Instant arrivalStatusCollectedAt,
		int arrivalStatusItemCount,
		Instant passengerForecastCollectedAt,
		int passengerForecastItemCount,
		Instant railroadOperationCollectedAt,
		int railroadOperationItemCount,
		String modelMessageId,
		Instant modelMeasuredAt
) {

	public CongestionInputReferences {
		Objects.requireNonNull(arrivalStatusCollectedAt, "arrivalStatusCollectedAt");
		Objects.requireNonNull(passengerForecastCollectedAt, "passengerForecastCollectedAt");
		Objects.requireNonNull(railroadOperationCollectedAt, "railroadOperationCollectedAt");
		Objects.requireNonNull(modelMessageId, "modelMessageId");
		Objects.requireNonNull(modelMeasuredAt, "modelMeasuredAt");
		if (arrivalStatusItemCount < 0 || passengerForecastItemCount < 0 || railroadOperationItemCount < 0) {
			throw new IllegalArgumentException("item counts must be non-negative");
		}
	}
}
