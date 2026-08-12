package com.drrk.collector.congestion;

import java.util.concurrent.atomic.AtomicReference;

public class LatestCongestionInputStore {

	private final AtomicReference<CongestionInputState> state =
			new AtomicReference<>(CongestionInputState.empty());

	public void replaceArrivalStatus(ArrivalStatusSnapshot snapshot) {
		state.updateAndGet(current -> current.withArrivalStatus(snapshot));
	}

	public void replacePassengerForecast(PassengerForecastSnapshot snapshot) {
		state.updateAndGet(current -> current.withPassengerForecast(snapshot));
	}

	public void replaceRailroadOperation(RailroadOperationSnapshot snapshot) {
		state.updateAndGet(current -> current.withRailroadOperation(snapshot));
	}

	public boolean replaceModelIfNewer(ModelMeasurementSnapshot snapshot) {
		while (true) {
			CongestionInputState current = state.get();
			ModelMeasurementSnapshot existing = current.modelMeasurement();
			if (existing != null && !snapshot.measuredAt().isAfter(existing.measuredAt())) {
				return false;
			}
			if (state.compareAndSet(current, current.withModelMeasurement(snapshot))) {
				return true;
			}
		}
	}

	public CongestionInputState snapshot() {
		return state.get();
	}
}
