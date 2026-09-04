package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Instant;

public record CongestionSnapshot(
		CongestionCalculatedMessage message,
		CongestionDeliveryStatus deliveryStatus,
		int retryCount,
		Instant receivedAt
) {
	public CongestionSnapshot {
		if (retryCount < 0) {
			throw new IllegalArgumentException("retryCount must be non-negative");
		}
	}
}
