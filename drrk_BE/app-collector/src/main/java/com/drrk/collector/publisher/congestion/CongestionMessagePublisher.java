package com.drrk.collector.publisher.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;

public interface CongestionMessagePublisher {

	void publish(CongestionCalculatedMessage message);
}
