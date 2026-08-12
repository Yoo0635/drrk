package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.PassengerForecastItem;
import com.drrk.collector.congestion.PassengerForecastSnapshot;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public class PassengerForecastMapper {

	public PassengerForecastSnapshot map(PassengerForecastApiResponse apiResponse, Instant collectedAt) {
		apiResponse.response().header().requireSuccess();
		List<PassengerForecastApiResponse.Item> items = apiResponse.response().body().items();
		if (items == null) {
			items = List.of();
		}
		List<PassengerForecastItem> selected = items.stream()
				.map(item -> new PassengerForecastItem(item.atime(), passengerCount(item.t1egsum1())))
				.toList();
		return new PassengerForecastSnapshot(collectedAt, selected);
	}

	private int passengerCount(String value) {
		return new BigDecimal(value).intValueExact();
	}
}
