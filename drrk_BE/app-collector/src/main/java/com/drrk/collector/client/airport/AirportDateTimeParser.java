package com.drrk.collector.client.airport;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.Optional;

final class AirportDateTimeParser {

	private static final ZoneId AIRPORT_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	private AirportDateTimeParser() {
	}

	static Optional<Instant> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		String digits = value.replaceAll("[^0-9]", "");
		try {
			DateTimeFormatter formatter = switch (digits.length()) {
				case 12 -> MINUTE_FORMAT;
				case 14 -> SECOND_FORMAT;
				default -> null;
			};
			if (formatter == null) {
				return Optional.empty();
			}
			return Optional.of(LocalDateTime.parse(digits, formatter).atZone(AIRPORT_ZONE).toInstant());
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}
}
