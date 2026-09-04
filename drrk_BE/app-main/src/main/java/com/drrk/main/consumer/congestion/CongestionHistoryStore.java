package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Instant;
import java.util.Optional;

interface CongestionHistoryStore {

	Optional<String> findPayload(String messageId);

	boolean insertIfAbsent(CongestionCalculatedMessage message, String payload, Instant receivedAt);

	int deleteExpired(Instant cutoff, int limit);
}
