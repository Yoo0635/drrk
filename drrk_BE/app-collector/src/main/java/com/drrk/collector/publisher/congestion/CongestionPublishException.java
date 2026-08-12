package com.drrk.collector.publisher.congestion;

public class CongestionPublishException extends RuntimeException {

	public CongestionPublishException(String message) {
		super(message);
	}

	public CongestionPublishException(String message, Throwable cause) {
		super(message, cause);
	}
}
