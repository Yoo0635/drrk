package com.drrk.collector.client.airport;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "airport.api")
public record AirportApiProperties(
		int numOfRows,
		Source arrivalStatus,
		Source passengerForecast,
		RailroadSource railroad
) {

	public record Source(String url, String key) {
	}

	public record RailroadSource(String url, String key, String trainClass, String stationCode) {
	}
}
