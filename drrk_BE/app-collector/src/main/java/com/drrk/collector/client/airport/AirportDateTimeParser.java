package com.drrk.collector.client.airport;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.format.ResolverStyle;
import java.util.Optional;

final class AirportDateTimeParser {

	private static final ZoneId AIRPORT_ZONE = ZoneId.of("Asia/Seoul");
	private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmm")
			.withResolverStyle(ResolverStyle.STRICT);
	private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
			.withResolverStyle(ResolverStyle.STRICT);

	private AirportDateTimeParser() {
	}

	static Optional<Instant> parse(String value) {
		if (value == null || value.isBlank()) {
			return Optional.empty();
		}
		// Validate input is exactly 12 or 14 digits (no surrounding text or malformed input)
		if (!value.matches("\\d{12}") && !value.matches("\\d{14}")) {
			return Optional.empty();
		}
		try {
			DateTimeFormatter formatter = switch (value.length()) {
				case 12 -> MINUTE_FORMAT;
				case 14 -> SECOND_FORMAT;
				default -> null;
			};
			if (formatter == null) {
				return Optional.empty();
			}
			return Optional.of(LocalDateTime.parse(value, formatter).atZone(AIRPORT_ZONE).toInstant());
		} catch (DateTimeParseException exception) {
			return Optional.empty();
		}
	}
}
