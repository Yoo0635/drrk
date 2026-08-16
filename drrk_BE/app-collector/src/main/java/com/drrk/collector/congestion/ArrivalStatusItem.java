package com.drrk.collector.congestion;

public record ArrivalStatusItem(
		String entryGate,
		String flightId,
		String effectiveArrivalTime,
		int koreanPassengerCount,
		int foreignPassengerCount
) {

	public ArrivalStatusItem(String flightId, String estimatedTime) {
		this(null, flightId, estimatedTime, 0, 0);
	}
}
