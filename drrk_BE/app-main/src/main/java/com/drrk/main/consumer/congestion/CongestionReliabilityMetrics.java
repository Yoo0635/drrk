package com.drrk.main.consumer.congestion;

import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CongestionReliabilityMetrics {

	private static final String RETRY_PUBLISHED = "drrk.congestion.retry.published";
	private static final String RETRY_RECOVERED = "drrk.congestion.retry.recovered";
	private static final String DEAD_LETTERED = "drrk.congestion.dead.lettered";

	private final MeterRegistry meterRegistry;

	public CongestionReliabilityMetrics(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	void retryPublished(int retryCount) {
		meterRegistry.counter(RETRY_PUBLISHED, "retry_count", Integer.toString(retryCount)).increment();
	}

	void recovered(int retryCount) {
		meterRegistry.counter(RETRY_RECOVERED, "retry_count", Integer.toString(retryCount)).increment();
	}

	void deadLettered(String reason) {
		meterRegistry.counter(DEAD_LETTERED, "reason", reason).increment();
	}
}
