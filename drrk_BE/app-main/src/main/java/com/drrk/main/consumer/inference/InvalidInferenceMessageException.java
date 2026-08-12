package com.drrk.main.consumer.inference;

public class InvalidInferenceMessageException extends RuntimeException {

	public InvalidInferenceMessageException(String message) {
		super(message);
	}

	public InvalidInferenceMessageException(String message, Throwable cause) {
		super(message, cause);
	}
}
