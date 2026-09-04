package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.util.Optional;

public interface CongestionResultHandler {

	default Optional<CongestionSnapshot> handle(CongestionCalculatedMessage message) {
		return handle(message, "", CongestionDeliveryStatus.LIVE, 0);
	}

	Optional<CongestionSnapshot> handle(
			CongestionCalculatedMessage message,
			String payload,
			CongestionDeliveryStatus deliveryStatus,
			int retryCount
	);
}
