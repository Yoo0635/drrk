package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;

public interface CongestionResultHandler {

	void handle(CongestionCalculatedMessage message);
}
