package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.ArrivalStatusItem;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class ArrivalStatusMapper {

	private static final int MAX_UPCOMING_ITEMS = 5;

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
				.filter(candidate -> !candidate.arrivalTime().isBefore(collectedAt))
				.sorted(Comparator.comparing(ArrivalCandidate::arrivalTime))
				.limit(MAX_UPCOMING_ITEMS)
				.map(ArrivalCandidate::item)
				.toList();
		return new ArrivalStatusSnapshot(collectedAt, selected);
	}

	private boolean isTerminalOneGateBOrC(ArrivalStatusApiResponse.Item item) {
		String entryGate = normalize(item.entrygate());
		return "T1".equalsIgnoreCase(normalize(item.terno()))
				&& ("B".equalsIgnoreCase(entryGate) || "C".equalsIgnoreCase(entryGate));
	}

	private Optional<ArrivalCandidate> mapItem(ArrivalStatusApiResponse.Item item) {
		String flightId = normalize(item.flightid());
		String estimatedArrivalTime = normalize(item.estimatedtime());
		Optional<Instant> arrivalTime = AirportDateTimeParser.parse(estimatedArrivalTime);
		if (flightId.isBlank() || arrivalTime.isEmpty()) {
			return Optional.empty();
		}
		Integer koreanPassengerCount = parseNonNegativeInteger(item.korean());
		Integer foreignPassengerCount = parseNonNegativeInteger(item.foreigner());
		if (koreanPassengerCount == null || foreignPassengerCount == null) {
			return Optional.empty();
		}
		return Optional.of(new ArrivalCandidate(
				new ArrivalStatusItem(
						normalize(item.entrygate()).toUpperCase(Locale.ROOT),
						flightId,
						estimatedArrivalTime,
						koreanPassengerCount,
						foreignPassengerCount
				),
				arrivalTime.orElseThrow()
		));
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

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private record ArrivalCandidate(ArrivalStatusItem item, Instant arrivalTime) {
	}
}
