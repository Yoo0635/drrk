package com.drrk.collector.client.airport;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AirportApiHeader(String resultCode, String resultMsg) {

	public void requireSuccess() {
		if (!"00".equals(resultCode)) {
			throw new AirportApiResponseException(resultCode, resultMsg);
		}
	}
}
