package com.drrk.collector.congestion;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

public record CongestionInputState(
		ArrivalStatusSnapshot arrivalStatus,
		PassengerForecastSnapshot passengerForecast,
		RailroadOperationSnapshot railroadOperation,
		ModelMeasurementSnapshot modelMeasurement
) {

	public static CongestionInputState empty() {
		return new CongestionInputState(null, null, null, null);
	}

	CongestionInputState withArrivalStatus(ArrivalStatusSnapshot value) {
		return new CongestionInputState(value, passengerForecast, railroadOperation, modelMeasurement);
	}

	CongestionInputState withPassengerForecast(PassengerForecastSnapshot value) {
		return new CongestionInputState(arrivalStatus, value, railroadOperation, modelMeasurement);
	}

	CongestionInputState withRailroadOperation(RailroadOperationSnapshot value) {
		return new CongestionInputState(arrivalStatus, passengerForecast, value, modelMeasurement);
	}

	CongestionInputState withModelMeasurement(ModelMeasurementSnapshot value) {
		return new CongestionInputState(arrivalStatus, passengerForecast, railroadOperation, value);
	}

	public Optional<CongestionInputs> freshInputs(
			Instant now,
			Duration apiMaxAge,
			Duration modelMaxAge
	) {
		if (arrivalStatus == null || passengerForecast == null || railroadOperation == null || modelMeasurement == null) {
			return Optional.empty();
		}
		if (!isFresh(arrivalStatus.collectedAt(), now, apiMaxAge)
				|| !isFresh(passengerForecast.collectedAt(), now, apiMaxAge)
				|| !isFresh(railroadOperation.collectedAt(), now, apiMaxAge)
				|| !isFresh(modelMeasurement.measuredAt(), now, modelMaxAge)) {
			return Optional.empty();
		}
		return Optional.of(new CongestionInputs(
				arrivalStatus,
				passengerForecast,
				railroadOperation,
				modelMeasurement
		));
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}
}
