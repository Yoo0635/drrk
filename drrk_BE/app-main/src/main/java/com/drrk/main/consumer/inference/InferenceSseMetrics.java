package com.drrk.main.consumer.inference;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class InferenceSseMetrics {

	public InferenceSseMetrics(MeterRegistry meterRegistry, InferenceSseBroadcaster broadcaster) {
		Gauge.builder(
						"drrk.inference.sse.active.connections",
						broadcaster,
						InferenceSseBroadcaster::activeEmitterCount
				)
				.description("Active inference SSE connections on this app-main instance")
				.register(meterRegistry);
	}
}
