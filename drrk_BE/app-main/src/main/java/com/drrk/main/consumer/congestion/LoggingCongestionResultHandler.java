package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LoggingCongestionResultHandler implements CongestionResultHandler {

	private static final Logger log = LoggerFactory.getLogger(LoggingCongestionResultHandler.class);

	@Override
	public void handle(CongestionCalculatedMessage message) {
		log.info("[CONSUME SUCCESS] messageId={} status={} calculatedAt={} modelMessageId={}",
				message.messageId(),
				message.status(),
				message.calculatedAt(),
				message.inputs().modelMessageId());
	}
}
