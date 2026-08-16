package com.drrk.collector.congestion;

import java.time.Instant;
import java.util.List;

public record RailroadOperationSnapshot(Instant collectedAt, List<RailroadOperationItem> items) {

	public RailroadOperationSnapshot {
		items = List.copyOf(items);
	}
}
