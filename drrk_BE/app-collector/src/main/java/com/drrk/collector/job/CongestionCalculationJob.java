package com.drrk.collector.job;

import com.drrk.collector.congestion.CongestionCalculator;
import com.drrk.collector.congestion.CongestionInputs;
import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.publisher.congestion.CongestionMessagePublisher;
import com.drrk.messaging.congestion.CongestionCalculatedMessage;
import java.time.Clock;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

public class CongestionCalculationJob {

	private static final Logger log = LoggerFactory.getLogger(CongestionCalculationJob.class);

	private final LatestCongestionInputStore store;
	private final CongestionCalculator calculator;
	private final CongestionMessagePublisher publisher;
	private final Clock clock;
	private final Duration apiMaxAge;
	private final Duration modelMaxAge;

	public CongestionCalculationJob(
			LatestCongestionInputStore store,
			CongestionCalculator calculator,
			CongestionMessagePublisher publisher,
			Clock clock,
			Duration apiMaxAge,
			Duration modelMaxAge
	) {
		this.store = store;
		this.calculator = calculator;
		this.publisher = publisher;
		this.clock = clock;
		this.apiMaxAge = apiMaxAge;
		this.modelMaxAge = modelMaxAge;
	}

	@Scheduled(
			fixedRateString = "${congestion.calculation.fixed-rate:PT10S}",
			initialDelayString = "${congestion.calculation.initial-delay:PT10S}"
	)
	public void calculateAndPublish() {
		store.snapshot().freshInputs(clock.instant(), apiMaxAge, modelMaxAge).ifPresentOrElse(
				this::calculateAndPublish,
				() -> log.info("[CONGESTION SKIPPED] reason=MISSING_OR_STALE_INPUT")
		);
	}

	private void calculateAndPublish(CongestionInputs inputs) {
		CongestionCalculatedMessage message;
		try {
			message = calculator.calculate(inputs);
		} catch (RuntimeException exception) {
			log.warn("[CALCULATION FAILED] errorType={}", exception.getClass().getSimpleName());
			return;
		}

		try {
			publisher.publish(message);
		} catch (RuntimeException exception) {
			log.warn("[PUBLISH FAILED] messageId={} errorType={}",
					message.messageId(), exception.getClass().getSimpleName());
			return;
		}
		log.info("[CONGESTION PUBLISHED] messageId={} status={} calculatedAt={}",
				message.messageId(), message.status(), message.calculatedAt());
	}
}
