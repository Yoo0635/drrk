package com.drrk.main.consumer.inference;

import java.time.Instant;

public record LatestInferenceSnapshot(
		String messageId,
		String spaceId,
		Instant windowEndedAt,
		int carrierCount
) {
}
