package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

public class RailroadOperationMapper {

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
				.toList();
		return new RailroadOperationSnapshot(collectedAt, selected);
	}

	private Optional<RailroadOperationItem> mapItem(RailroadOperationApiResponse.Item item) {
		String scheduledArrivalTime = normalize(item.planArrvDttm());
		if (scheduledArrivalTime.isBlank()) {
			return Optional.empty();
		}
		return Optional.of(new RailroadOperationItem(
				normalize(item.trnNo()),
				normalize(item.stnCd()),
				scheduledArrivalTime,
				normalizeBlankToNull(item.accomArrvDttm()),
				normalize(item.trnClsNm())
		));
	}

	private String normalizeBlankToNull(String value) {
		String normalized = normalize(value);
		return normalized.isBlank() ? null : normalized;
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
