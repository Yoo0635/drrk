package com.drrk.main.consumer.inference;

import com.drrk.main.consumer.congestion.CongestionDeliveryStatus;
import java.time.Instant;

record CongestionDeliveryStreamResponse(
		String messageId,
		Instant calculatedAt,
		double score,
		String level,
		CongestionDeliveryStatus deliveryStatus,
		int retryCount
) {
}
