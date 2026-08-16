package com.drrk.collector.congestion;

import java.time.Instant;

public record ModelMeasurementSnapshot(
		String messageId,
		Instant measuredAt,
		String spaceId,
		int windowSec,
		long carrierCount
) {

	public ModelMeasurementSnapshot(String messageId, Instant measuredAt, int carrierCount, double ignoredIntensity) {
		this(messageId, measuredAt, "", 0, carrierCount);
	}
}
