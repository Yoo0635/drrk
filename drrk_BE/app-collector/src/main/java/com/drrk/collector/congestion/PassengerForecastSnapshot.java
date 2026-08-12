package com.drrk.collector.congestion;

import java.time.Instant;
import java.util.List;

public record PassengerForecastSnapshot(Instant collectedAt, List<PassengerForecastItem> items) {

	public PassengerForecastSnapshot {
		items = List.copyOf(items);
	}
}
