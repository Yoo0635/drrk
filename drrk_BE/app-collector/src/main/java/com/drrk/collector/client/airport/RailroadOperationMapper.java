package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.RailroadOperationItem;
import com.drrk.collector.congestion.RailroadOperationSnapshot;
import java.time.Instant;
import java.util.List;

public class RailroadOperationMapper {

	public RailroadOperationSnapshot map(RailroadOperationApiResponse apiResponse, Instant collectedAt) {
		apiResponse.response().header().requireSuccess();
		List<RailroadOperationApiResponse.Item> items = apiResponse.response().body().items();
		if (items == null) {
			items = List.of();
		}
		List<RailroadOperationItem> selected = items.stream()
				.map(item -> new RailroadOperationItem(
						item.trnNo(),
						item.planDptrDttm(),
						item.planArrvDttm()
				))
				.toList();
		return new RailroadOperationSnapshot(collectedAt, selected);
	}
}
