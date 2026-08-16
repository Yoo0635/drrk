package com.drrk.collector.client.airport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ArrivalStatusApiResponse(Response response) {

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Response(AirportApiHeader header, Body body) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Body(Items items) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Items(List<Item> item) {
	}

	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Item(
			String terno,
			String entrygate,
			String flightid,
			String estimatedtime,
			String scheduletime,
			String korean,
			String foreigner
	) {
	}
}
