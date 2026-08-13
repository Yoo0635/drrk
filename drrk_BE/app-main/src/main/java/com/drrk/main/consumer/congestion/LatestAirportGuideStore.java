package com.drrk.main.consumer.congestion;

import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import com.drrk.messaging.congestion.CongestionCalculationStatus;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LatestAirportGuideStore implements CongestionResultHandler {

	private static final Logger log = LoggerFactory.getLogger(LatestAirportGuideStore.class);

	private final AtomicReference<CongestionCalculatedMessage> latest = new AtomicReference<>();

	@Override
	public void handle(CongestionCalculatedMessage message) {
		if (message.status() == CongestionCalculationStatus.CALCULATED) {
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
		}
	}

	public Optional<CongestionCalculatedMessage> latest() {
		return Optional.ofNullable(latest.get());
	}
}
