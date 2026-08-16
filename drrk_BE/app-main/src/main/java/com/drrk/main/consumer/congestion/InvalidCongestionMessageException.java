package com.drrk.main.consumer.congestion;

public class InvalidCongestionMessageException extends RuntimeException {

	public InvalidCongestionMessageException(String message) {
		super(message);
	}

	public InvalidCongestionMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
