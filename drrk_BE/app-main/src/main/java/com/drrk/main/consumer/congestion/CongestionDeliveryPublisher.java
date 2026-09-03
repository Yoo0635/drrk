package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;

public interface CongestionDeliveryPublisher {

	void publish(
			CongestionCalculatedMessage message,
			CongestionDeliveryStatus deliveryStatus,
			int retryCount
	);
}
