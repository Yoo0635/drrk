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

	/**
	 * 계산에 쓸 입력을 만든다.
	 *
	 * <p><b>모델 계측만 필수</b>다. 공항·철도 API 스냅샷이 없거나 오래됐으면 빈 스냅샷으로
	 * 대체해 넘기고, 그 결과 어떤 상태(NO_FLIGHT_DATA / 배차 간격 가정)로 산출할지는
	 * 계산기가 판단한다. 외부 API 장애가 계측 기반 서비스를 통째로 멈추지 않게 하기 위함이다.</p>
	 */
	public Optional<CongestionInputs> freshInputs(
			Instant now,
			Duration apiMaxAge,
			Duration modelMaxAge
	) {
		if (modelMeasurements.isEmpty()) {
			return Optional.empty();
		}
		ModelMeasurementSnapshot latestMeasurement = modelMeasurements.get(modelMeasurements.size() - 1);
		if (!isFresh(latestMeasurement.measuredAt(), now, modelMaxAge)) {
			return Optional.empty();
		}
		return Optional.of(new CongestionInputs(
				usableOrEmptyArrivalStatus(now, apiMaxAge),
				usableOrEmptyPassengerForecast(now, apiMaxAge),
				usableOrEmptyRailroadOperation(now, apiMaxAge),
				modelMeasurements
		));
	}

	private ArrivalStatusSnapshot usableOrEmptyArrivalStatus(Instant now, Duration apiMaxAge) {
		return arrivalStatus != null && isFresh(arrivalStatus.collectedAt(), now, apiMaxAge)
				? arrivalStatus
				: new ArrivalStatusSnapshot(now, List.of());
	}

	private PassengerForecastSnapshot usableOrEmptyPassengerForecast(Instant now, Duration apiMaxAge) {
		return passengerForecast != null && isFresh(passengerForecast.collectedAt(), now, apiMaxAge)
				? passengerForecast
				: new PassengerForecastSnapshot(now, List.of());
	}

	private RailroadOperationSnapshot usableOrEmptyRailroadOperation(Instant now, Duration apiMaxAge) {
		return railroadOperation != null && isFresh(railroadOperation.collectedAt(), now, apiMaxAge)
				? railroadOperation
				: new RailroadOperationSnapshot(now, List.of());
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}
}
