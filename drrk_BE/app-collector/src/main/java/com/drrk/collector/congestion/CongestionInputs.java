package com.drrk.collector.congestion;

import java.util.List;

public record CongestionInputs(
		ArrivalStatusSnapshot arrivalStatus,
		PassengerForecastSnapshot passengerForecast,
		RailroadOperationSnapshot railroadOperation,
		List<ModelMeasurementSnapshot> modelMeasurements
) {

	public CongestionInputs {
		modelMeasurements = List.copyOf(modelMeasurements);
	}
}
