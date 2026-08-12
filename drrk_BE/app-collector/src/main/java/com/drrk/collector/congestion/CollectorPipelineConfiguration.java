package com.drrk.collector.congestion;

import com.drrk.collector.job.CongestionCalculationJob;
import com.drrk.collector.publisher.congestion.CongestionMessagePublisher;
import java.time.Clock;
import java.util.UUID;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(CongestionCalculationProperties.class)
public class CollectorPipelineConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	LatestCongestionInputStore latestCongestionInputStore() {
		return new LatestCongestionInputStore();
	}

	@Bean
	CongestionCalculator congestionCalculator(Clock clock) {
		return new FormulaPendingCongestionCalculator(clock, UUID::randomUUID);
	}

	@Bean
	@ConditionalOnProperty(
			prefix = "congestion.calculation",
			name = "enabled",
			havingValue = "true"
	)
	CongestionCalculationJob congestionCalculationJob(
			LatestCongestionInputStore store,
			CongestionCalculator calculator,
			CongestionMessagePublisher publisher,
			Clock clock,
			CongestionCalculationProperties properties
	) {
		return new CongestionCalculationJob(
				store,
				calculator,
				publisher,
				clock,
				properties.getApiMaxAge(),
				properties.getModelMaxAge()
		);
	}
}
