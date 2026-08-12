package com.drrk.collector.congestion;

public record CongestionInputs(
		ArrivalStatusSnapshot arrivalStatus,
		PassengerForecastSnapshot passengerForecast,
		RailroadOperationSnapshot railroadOperation,
		ModelMeasurementSnapshot modelMeasurement
) {
}
