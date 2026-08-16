package com.drrk.collector.congestion;

import java.util.concurrent.atomic.AtomicReference;
import java.util.List;
import java.util.ArrayList;

public class LatestCongestionInputStore {

	private static final int MAX_MEASUREMENT_HISTORY = 256;

	private final String acceptedSensorSpaceId;
	private final AtomicReference<CongestionInputState> state =
			new AtomicReference<>(CongestionInputState.empty());

	public LatestCongestionInputStore() {
		this(null);
	}

	public LatestCongestionInputStore(String acceptedSensorSpaceId) {
		this.acceptedSensorSpaceId = acceptedSensorSpaceId;
	}

	public void replaceArrivalStatus(ArrivalStatusSnapshot snapshot) {
		state.updateAndGet(current -> current.withArrivalStatus(snapshot));
	}

	public void replacePassengerForecast(PassengerForecastSnapshot snapshot) {
		state.updateAndGet(current -> current.withPassengerForecast(snapshot));
	}

	public void replaceRailroadOperation(RailroadOperationSnapshot snapshot) {
		state.updateAndGet(current -> current.withRailroadOperation(snapshot));
	}

	/**
	 * 이 저장소가 받아들이는 센서 space_id인지 여부. 불일치를 조용히 버리지 않고
	 * 호출부가 구분해 로깅할 수 있게 공개한다.
	 */
	public boolean accepts(String spaceId) {
		return acceptedSensorSpaceId == null || acceptedSensorSpaceId.equals(spaceId);
	}

	public String acceptedSensorSpaceId() {
		return acceptedSensorSpaceId;
	}

	public boolean replaceModelIfNewer(ModelMeasurementSnapshot snapshot) {
		if (!accepts(snapshot.spaceId())) {
			return false;
		}
		while (true) {
			CongestionInputState current = state.get();
			List<ModelMeasurementSnapshot> existingMeasurements = current.modelMeasurements();
			ModelMeasurementSnapshot existing = existingMeasurements.isEmpty()
					? null
					: existingMeasurements.get(existingMeasurements.size() - 1);
			if (existing != null && !snapshot.measuredAt().isAfter(existing.measuredAt())) {
				return false;
			}
			List<ModelMeasurementSnapshot> updated = new ArrayList<>(existingMeasurements);
			updated.add(snapshot);
			if (updated.size() > MAX_MEASUREMENT_HISTORY) {
				updated = new ArrayList<>(updated.subList(updated.size() - MAX_MEASUREMENT_HISTORY, updated.size()));
			}
			if (state.compareAndSet(current, current.withModelMeasurements(updated))) {
				return true;
			}
		}
	}

	public CongestionInputState snapshot() {
		return state.get();
	}
}
