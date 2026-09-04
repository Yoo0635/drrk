package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

class PersistentCongestionResultHandler implements CongestionResultHandler {

	private static final Logger log = LoggerFactory.getLogger(PersistentCongestionResultHandler.class);

	private final CongestionHistoryIngestionService historyIngestionService;
	private final LatestAirportGuideStore latestStore;

	PersistentCongestionResultHandler(
			CongestionHistoryIngestionService historyIngestionService,
			LatestAirportGuideStore latestStore
	) {
		this.historyIngestionService = historyIngestionService;
		this.latestStore = latestStore;
	}

	@Override
	public Optional<CongestionSnapshot> handle(
			CongestionCalculatedMessage message,
			String payload,
			CongestionDeliveryStatus deliveryStatus,
			int retryCount
	) {
		CongestionHistoryIngestionResult result = historyIngestionService.ingest(message, payload);
		if (result == CongestionHistoryIngestionResult.TOO_OLD) {
			return Optional.empty();
		}
		if (result == CongestionHistoryIngestionResult.DUPLICATE) {
			log.info("[CONGESTION HISTORY DUPLICATE] messageId={}", message.messageId());
		}
		return latestStore.handle(message, deliveryStatus, retryCount);
	}
}
