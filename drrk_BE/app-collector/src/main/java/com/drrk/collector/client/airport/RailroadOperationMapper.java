package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RailroadOperationMapper {

	private static final int MAX_UPCOMING_ITEMS = 5;

	public RailroadOperationSnapshot map(RailroadOperationApiResponse apiResponse, Instant collectedAt) {
		apiResponse.response().header().requireSuccess();
		List<RailroadOperationApiResponse.Item> items = apiResponse.response().body() == null
				? List.of()
				: apiResponse.response().body().items();
		if (items == null) {
			items = List.of();
		}
		List<RailroadCandidate> candidates = items.stream()
				.filter(Objects::nonNull)
				.map(this::mapItem)
				.flatMap(Optional::stream)
				.sorted(Comparator.comparing(RailroadCandidate::arrivalTime))
				.toList();
		Optional<RailroadCandidate> latestPast = candidates.stream()
				.filter(candidate -> candidate.arrivalTime().isBefore(collectedAt))
				.max(Comparator.comparing(RailroadCandidate::arrivalTime));
		int upcomingLimit = MAX_UPCOMING_ITEMS - (latestPast.isPresent() ? 1 : 0);
		List<RailroadCandidate> selectedCandidates = new ArrayList<>();
		latestPast.ifPresent(selectedCandidates::add);
		selectedCandidates.addAll(candidates.stream()
				.filter(candidate -> !candidate.arrivalTime().isBefore(collectedAt))
				.limit(upcomingLimit)
				.toList());
		List<RailroadOperationItem> selected = selectedCandidates.stream()
				.map(RailroadCandidate::item)
				.toList();
		return new RailroadOperationSnapshot(collectedAt, selected);
	}

	private Optional<RailroadCandidate> mapItem(RailroadOperationApiResponse.Item item) {
		String trainNumber = normalize(item.trnNo());
		String stationCode = normalize(item.stnCd());
		String arrivalTimeText = normalize(item.accomArrvDttm());

		// Validate identity fields are not blank
		if (trainNumber.isBlank() || stationCode.isBlank()) {
			return Optional.empty();
		}

		Optional<Instant> arrivalTime = AirportDateTimeParser.parse(arrivalTimeText);
		if (arrivalTime.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new RailroadCandidate(
				new RailroadOperationItem(
						trainNumber,
						stationCode,
						arrivalTimeText,
						null,
						normalizeBlankToNull(item.planDptrDttm()),
						normalizeBlankToNull(item.accomDptrDttm()),
						normalize(item.trnClsfNm())
				),
				arrivalTime.orElseThrow()
		));
	}

	private String normalizeBlankToNull(String value) {
		String normalized = normalize(value);
		return normalized.isBlank() ? null : normalized;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}

	private record RailroadCandidate(RailroadOperationItem item, Instant arrivalTime) {
	}
}
