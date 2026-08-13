package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Instant;
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
		List<RailroadOperationItem> selected = items.stream()
				.filter(Objects::nonNull)
				.map(this::mapItem)
				.flatMap(Optional::stream)
				.filter(candidate -> !candidate.arrivalTime().isBefore(collectedAt))
				.sorted(Comparator.comparing(RailroadCandidate::arrivalTime))
				.limit(MAX_UPCOMING_ITEMS)
				.map(RailroadCandidate::item)
				.toList();
		return new RailroadOperationSnapshot(collectedAt, selected);
	}

	private Optional<RailroadCandidate> mapItem(RailroadOperationApiResponse.Item item) {
		String arrivalTimeText = normalize(item.accomArrvDttm());
		Optional<Instant> arrivalTime = AirportDateTimeParser.parse(arrivalTimeText);
		if (arrivalTime.isEmpty()) {
			return Optional.empty();
		}
		return Optional.of(new RailroadCandidate(
				new RailroadOperationItem(
						normalize(item.trnNo()),
						normalize(item.stnCd()),
						arrivalTimeText,
						null,
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
