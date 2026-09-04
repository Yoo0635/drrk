package com.drrk.main.consumer.inference;

import com.drrk.main.consumer.congestion.CongestionDeliveryStatus;
import java.time.Instant;
import java.util.List;

record CongestionHistoryStreamResponse(
		Instant serverNow,
		Instant windowStart,
		List<CongestionHistorySampleResponse> samples
) {
}

record CongestionHistorySampleResponse(
		String messageId,
		Instant calculatedAt,
		double score,
		String level,
		CongestionDeliveryStatus deliveryStatus,
		int retryCount
) {
}
