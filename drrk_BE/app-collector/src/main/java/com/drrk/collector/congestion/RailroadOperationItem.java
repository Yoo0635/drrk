package com.drrk.collector.congestion;

public record RailroadOperationItem(
		String trainNumber,
		String stationCode,
		String scheduledArrivalTime,
		String actualArrivalTime,
		String trainType
) {

	public RailroadOperationItem(String trainNumber, String departureTime, String arrivalTime) {
		this(trainNumber, null, departureTime, arrivalTime, null);
	}
}
