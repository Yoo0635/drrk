package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.ArrivalStatusItem;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class ArrivalStatusMapper {

	private static final DateTimeFormatter MINUTE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmm");
	private static final DateTimeFormatter SECOND_FORMAT = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");

	public ArrivalStatusSnapshot map(ArrivalStatusApiResponse apiResponse, Instant collectedAt) {
		apiResponse.response().header().requireSuccess();
		List<ArrivalStatusApiResponse.Item> items = apiResponse.response().body() == null
				? List.of()
				: apiResponse.response().body().items();
		if (items == null) {
			items = List.of();
		}
		List<ArrivalStatusItem> selected = items.stream()
				.filter(Objects::nonNull)
				.filter(this::isTerminalOneGateBOrC)
				.map(this::mapItem)
				.flatMap(Optional::stream)
				.toList();
		return new ArrivalStatusSnapshot(collectedAt, selected);
	}

	private boolean isTerminalOneGateBOrC(ArrivalStatusApiResponse.Item item) {
		String entryGate = normalize(item.entrygate());
		return "T1".equalsIgnoreCase(normalize(item.terno()))
				&& ("B".equalsIgnoreCase(entryGate) || "C".equalsIgnoreCase(entryGate));
	}

	private Optional<ArrivalStatusItem> mapItem(ArrivalStatusApiResponse.Item item) {
		String flightId = normalize(item.flightid());
		String effectiveArrivalTime = firstText(item.estimatedtime(), item.scheduletime());
		if (flightId.isBlank() || effectiveArrivalTime == null || !isAirportDateTime(effectiveArrivalTime)) {
			return Optional.empty();
		}
		Integer koreanPassengerCount = parseNonNegativeInteger(item.korean());
		Integer foreignPassengerCount = parseNonNegativeInteger(item.foreigner());
		if (koreanPassengerCount == null || foreignPassengerCount == null) {
			return Optional.empty();
		}
		return Optional.of(new ArrivalStatusItem(
				normalize(item.entrygate()).toUpperCase(Locale.ROOT),
				flightId,
				effectiveArrivalTime,
				koreanPassengerCount,
				foreignPassengerCount
		));
	}

	private boolean isAirportDateTime(String value) {
		String digits = value.replaceAll("[^0-9]", "");
		try {
			if (digits.length() == 12) {
				LocalDateTime.parse(digits, MINUTE_FORMAT);
				return true;
			}
			if (digits.length() == 14) {
				LocalDateTime.parse(digits, SECOND_FORMAT);
				return true;
			}
			return false;
		} catch (DateTimeParseException exception) {
			return false;
		}
	}

	private Integer parseNonNegativeInteger(String value) {
		String normalized = normalize(value);
		if (!normalized.matches("\\d+")) {
			return null;
		}
		try {
			return Integer.parseInt(normalized);
		} catch (NumberFormatException exception) {
			return null;
		}
	}

	private String firstText(String first, String second) {
		String normalizedFirst = normalize(first);
		if (!normalizedFirst.isBlank()) {
			return normalizedFirst;
		}
		String normalizedSecond = normalize(second);
		if (!normalizedSecond.isBlank()) {
			return normalizedSecond;
		}
		return null;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
