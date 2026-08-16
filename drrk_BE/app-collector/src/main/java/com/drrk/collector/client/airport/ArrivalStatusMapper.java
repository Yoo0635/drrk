package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.ArrivalStatusItem;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

public class ArrivalStatusMapper {

	// v2 예보층은 "이미 착륙했지만 아직 출구로 나오지 않은" 편이 핵심이므로 과거 도착편도 남긴다.
	// (도착 후 45~90분 뒤 출구 통과 → 그 시점이 센서 통과 시각)
	private static final Duration ARRIVAL_LOOKBACK = Duration.ofHours(3);
	private static final Duration ARRIVAL_LOOKAHEAD = Duration.ofHours(3);
	private static final int MAX_ITEMS = 50;

	public ArrivalStatusSnapshot map(ArrivalStatusApiResponse apiResponse, Instant collectedAt) {
		if (apiResponse.response() == null || apiResponse.response().header() == null) {
			throw new AirportApiResponseException("MISSING_HEADER", "입국장현황 응답에 header가 없습니다");
		}
		apiResponse.response().header().requireSuccess();
		List<ArrivalStatusApiResponse.Item> items = apiResponse.response().body() == null
				|| apiResponse.response().body().items() == null
				? List.of()
				: apiResponse.response().body().items().item();
		if (items == null) {
			items = List.of();
		}
		Instant lowerBound = collectedAt.minus(ARRIVAL_LOOKBACK);
		Instant upperBound = collectedAt.plus(ARRIVAL_LOOKAHEAD);
		List<ArrivalStatusItem> selected = items.stream()
				.filter(Objects::nonNull)
				.filter(this::isTerminalOneGateBOrC)
				.map(this::mapItem)
				.flatMap(Optional::stream)
				.filter(candidate -> !candidate.arrivalTime().isBefore(lowerBound))
				.filter(candidate -> !candidate.arrivalTime().isAfter(upperBound))
				.sorted(Comparator.comparing(ArrivalCandidate::arrivalTime))
				.limit(MAX_ITEMS)
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
		// 기준시각 = estimatedtime, 없으면 scheduletime (공식 문서 3.2절)
		String estimatedArrivalTime = normalize(item.estimatedtime());
		if (estimatedArrivalTime.isBlank()) {
			estimatedArrivalTime = normalize(item.scheduletime());
		}
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

	/**
	 * 입국장현황 API는 승객 수를 {@code "120.0"} 처럼 소수점이 붙은 문자열로 내려준다.
	 * 정수 문자열과 소수 문자열을 모두 받아 반올림한다.
	 */
	private Integer parseNonNegativeInteger(String value) {
		String normalized = normalize(value);
		if (!normalized.matches("\\d+(\\.\\d+)?")) {
			return null;
		}
		try {
			return (int) Math.round(Double.parseDouble(normalized));
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
