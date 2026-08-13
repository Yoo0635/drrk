package com.drrk.main.consumer.inference;

import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.springframework.stereotype.Component;

@Component
public class LatestInferenceSnapshotStore {

	private final ConcurrentMap<String, LatestInferenceSnapshot> latestBySpace = new ConcurrentHashMap<>();

	public void updateIfLatest(LatestInferenceSnapshot snapshot) {
		latestBySpace.compute(snapshot.spaceId(), (spaceId, current) -> {
			if (current == null || snapshot.windowEndedAt().isAfter(current.windowEndedAt())) {
				return snapshot;
			}
			return current;
		});
	}

	public List<LatestInferenceSnapshot> findAll() {
		return latestBySpace.values().stream()
				.sorted(Comparator.comparing(LatestInferenceSnapshot::spaceId))
				.toList();
	}
}
