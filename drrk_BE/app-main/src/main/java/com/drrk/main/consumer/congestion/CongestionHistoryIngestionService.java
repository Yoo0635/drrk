package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.transaction.annotation.Transactional;

public class CongestionHistoryIngestionService {

	private static final Logger log = LoggerFactory.getLogger(CongestionHistoryIngestionService.class);

	private final CongestionHistoryStore store;
	private final Clock clock;
	private final Duration retention;
	private final int cleanupBatchSize;

	public CongestionHistoryIngestionService(
			CongestionHistoryStore store,
			Clock clock,
			@Value("${congestion.history.retention:PT720H}") Duration retention,
			@Value("${congestion.history.cleanup-batch-size:1000}") int cleanupBatchSize
	) {
		this.store = store;
		this.clock = clock;
		this.retention = retention;
		this.cleanupBatchSize = cleanupBatchSize;
	}

	@Transactional
	public CongestionHistoryIngestionResult ingest(CongestionCalculatedMessage message, String payload) {
		Instant now = clock.instant();
		Instant cutoff = now.minus(retention);
		if (!message.calculatedAt().isAfter(cutoff)) {
			log.info("[CONGESTION HISTORY SKIPPED] messageId={} calculatedAt={} reason=RETENTION_EXPIRED",
					message.messageId(), message.calculatedAt());
			return CongestionHistoryIngestionResult.TOO_OLD;
		}

		store.findPayload(message.messageId()).ifPresent(existingPayload -> {
			if (!existingPayload.equals(payload)) {
				throw new InvalidCongestionMessageException("same messageId has different congestion payload");
			}
		});

		boolean inserted = store.insertIfAbsent(message, payload, now);
		return inserted ? CongestionHistoryIngestionResult.STORED : CongestionHistoryIngestionResult.DUPLICATE;
	}

	@Scheduled(fixedRateString = "${congestion.history.cleanup-fixed-rate:PT1H}")
	@Transactional
	public void deleteExpiredHistory() {
		int deleted = store.deleteExpired(clock.instant().minus(retention), cleanupBatchSize);
		if (deleted > 0) {
			log.info("[CONGESTION HISTORY CLEANED] deleted={}", deleted);
		}
	}
}
