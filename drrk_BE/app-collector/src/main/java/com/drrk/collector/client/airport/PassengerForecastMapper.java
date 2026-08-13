package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.PassengerForecastItem;
import com.drrk.collector.congestion.PassengerForecastSnapshot;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

public class PassengerForecastMapper {

	public PassengerForecastSnapshot map(PassengerForecastApiResponse apiResponse, Instant collectedAt) {
		apiResponse.response().header().requireSuccess();
		List<PassengerForecastApiResponse.Item> items = apiResponse.response().body() == null
				? List.of()
				: apiResponse.response().body().items();
		if (items == null) {
			items = List.of();
		}
		List<PassengerForecastItem> selected = items.stream()
				.map(this::mapItem)
				.flatMap(Optional::stream)
				.toList();
		return new PassengerForecastSnapshot(collectedAt, selected);
	}

	private Optional<PassengerForecastItem> mapItem(PassengerForecastApiResponse.Item item) {
		Integer passengerCount = parseNonNegativeInteger(item.t1eg1());
		if (passengerCount == null) {
			return Optional.empty();
		}
		return Optional.of(new PassengerForecastItem(
				normalize(item.adate()),
				normalize(item.atime()),
				passengerCount
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
}
