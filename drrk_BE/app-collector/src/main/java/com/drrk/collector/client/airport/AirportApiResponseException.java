package com.drrk.collector.client.airport;

public class AirportApiResponseException extends RuntimeException {

	public AirportApiResponseException(String resultCode, String resultMessage) {
		super("Airport API returned failure resultCode=" + resultCode + ", resultMessage=" + resultMessage);
	}
}
