package com.drrk.collector.congestion;

import java.time.Instant;

public record ModelMeasurementSnapshot(
		String messageId,
		Instant measuredAt,
		int carrierCount,
		double intensity
) {
}
