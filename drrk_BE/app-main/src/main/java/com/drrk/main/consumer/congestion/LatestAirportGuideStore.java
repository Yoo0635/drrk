package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LatestAirportGuideStore implements CongestionResultHandler {

	private static final Logger log = LoggerFactory.getLogger(LatestAirportGuideStore.class);

	private final AtomicReference<CongestionCalculatedMessage> latest = new AtomicReference<>();

	@Override
	public void handle(CongestionCalculatedMessage message) {
		if (CongestionCalculatedMessage.hasScore(message.status())) {
			CongestionCalculatedMessage stored = latest.updateAndGet(
					current -> current == null || message.calculatedAt().isAfter(current.calculatedAt())
					? message
					: current
			);
			if (stored == message) {
				log.info("[AIRPORT GUIDE UPDATED] calculatedAt={} version={} score={} trainCount={}",
						message.calculatedAt(),
						message.calculationVersion(),
						message.score(),
						message.railroadArrivals().size());
			}
		} else {
			log.info("[AIRPORT GUIDE SKIPPED] status={} calculatedAt={} reason=NO_SCORE_IN_MESSAGE",
					message.status(), message.calculatedAt());
		}
	}

	public Optional<CongestionCalculatedMessage> latest() {
		return Optional.ofNullable(latest.get());
	}

	public Optional<CongestionCalculatedMessage> latestFresh(Instant now, Duration maxAge) {
		return latest()
				.filter(message -> isFresh(message.calculatedAt(), now, maxAge));
	}

	private static boolean isFresh(Instant timestamp, Instant now, Duration maxAge) {
		return !timestamp.isAfter(now) && Duration.between(timestamp, now).compareTo(maxAge) <= 0;
	}
}
