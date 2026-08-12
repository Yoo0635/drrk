package com.drrk.collector.congestion;

import java.time.Instant;
import java.util.List;

public record ArrivalStatusSnapshot(Instant collectedAt, List<ArrivalStatusItem> items) {

	public ArrivalStatusSnapshot {
		items = List.copyOf(items);
	}
}
