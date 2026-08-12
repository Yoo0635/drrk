package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.ArrivalStatusItem;
import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import java.time.Instant;
import java.util.List;

public class ArrivalStatusMapper {

	public ArrivalStatusSnapshot map(ArrivalStatusApiResponse apiResponse, Instant collectedAt) {
		apiResponse.response().header().requireSuccess();
		List<ArrivalStatusApiResponse.Item> items = apiResponse.response().body().items();
		if (items == null) {
			items = List.of();
		}
		List<ArrivalStatusItem> selected = items.stream()
				.filter(this::isTerminalOneGateB)
				.map(item -> new ArrivalStatusItem(item.flightid(), item.estimatedtime()))
				.toList();
		return new ArrivalStatusSnapshot(collectedAt, selected);
	}

	private boolean isTerminalOneGateB(ArrivalStatusApiResponse.Item item) {
		return "T1".equalsIgnoreCase(normalize(item.terno())) && "B".equalsIgnoreCase(normalize(item.entrygate()));
	}

	private String normalize(String value) {
		return value == null ? "" : value.trim();
	}
}
