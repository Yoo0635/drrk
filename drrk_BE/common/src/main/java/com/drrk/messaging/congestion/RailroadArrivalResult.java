package com.drrk.messaging.congestion;

import java.time.LocalTime;
import java.time.format.DateTimeParseException;
import java.util.Objects;

public record RailroadArrivalResult(
		String trainNo,
		String trainType,
		String scheduledArrivalTime,
		String actualArrivalTime,
		RailroadArrivalStatus status
) {

	public RailroadArrivalResult {
		requireText(trainNo, "trainNo");
		requireText(trainType, "trainType");
		validateTime(scheduledArrivalTime, "scheduledArrivalTime");
		if (actualArrivalTime != null) {
			validateTime(actualArrivalTime, "actualArrivalTime");
		}
		Objects.requireNonNull(status, "status");
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " must not be blank");
		}
	}

	private static void validateTime(String value, String field) {
		try {
			if (value == null || value.length() != 5) {
				throw new DateTimeParseException("Expected HH:mm", String.valueOf(value), 0);
			}
			LocalTime.parse(value);
		} catch (DateTimeParseException exception) {
			throw new IllegalArgumentException(field + " must use HH:mm", exception);
		}
	}
}
