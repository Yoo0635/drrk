package com.drrk.collector.congestion;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public record CongestionInputState(
		ArrivalStatusSnapshot arrivalStatus,
		PassengerForecastSnapshot passengerForecast,
		RailroadOperationSnapshot railroadOperation,
		List<ModelMeasurementSnapshot> modelMeasurements
) {

	public static CongestionInputState empty() {
		return new CongestionInputState(null, null, null, List.of());
	}

	CongestionInputState withArrivalStatus(ArrivalStatusSnapshot value) {
		return new CongestionInputState(value, passengerForecast, railroadOperation, modelMeasurements);
	}

	CongestionInputState withPassengerForecast(PassengerForecastSnapshot value) {
		return new CongestionInputState(arrivalStatus, value, railroadOperation, modelMeasurements);
	}

	CongestionInputState withRailroadOperation(RailroadOperationSnapshot value) {
		return new CongestionInputState(arrivalStatus, passengerForecast, value, modelMeasurements);
	}

	CongestionInputState withModelMeasurements(List<ModelMeasurementSnapshot> values) {
		return new CongestionInputState(arrivalStatus, passengerForecast, railroadOperation, List.copyOf(values));
	}

	public Optional<CongestionInputs> freshInputs(
			Instant now,
			Duration apiMaxAge,
			Duration modelMaxAge
	) {
		if (arrivalStatus == null || passengerForecast == null || railroadOperation == null || modelMeasurements.isEmpty()) {
			return Optional.empty();
		}
		ModelMeasurementSnapshot latestMeasurement = modelMeasurements.get(modelMeasurements.size() - 1);
		if (!isFresh(arrivalStatus.collectedAt(), now, apiMaxAge)
				|| !isFresh(passengerForecast.collectedAt(), now, apiMaxAge)
				|| !isFresh(railroadOperation.collectedAt(), now, apiMaxAge)
				|| !isFresh(latestMeasurement.measuredAt(), now, modelMaxAge)) {
			return Optional.empty();
		}
		return Optional.of(new CongestionInputs(
				arrivalStatus,
				passengerForecast,
				railroadOperation,
				modelMeasurements
		));
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}
}
