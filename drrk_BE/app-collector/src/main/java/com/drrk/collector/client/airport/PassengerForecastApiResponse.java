package com.drrk.collector.client.airport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PassengerForecastApiResponse(Response response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Response(AirportApiHeader header, Body body) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(List<Item> items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(String atime, String t1egsum1) {
	}
}
