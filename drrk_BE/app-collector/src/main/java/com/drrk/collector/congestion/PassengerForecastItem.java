package com.drrk.collector.congestion;

public record PassengerForecastItem(String date, String timeSlot, int expectedPassengerCount) {

	public PassengerForecastItem(String timeSlot, int expectedPassengerCount) {
		this(null, timeSlot, expectedPassengerCount);
	}
}
