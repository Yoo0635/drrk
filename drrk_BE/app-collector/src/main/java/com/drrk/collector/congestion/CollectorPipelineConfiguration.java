package com.drrk.collector.congestion;

import com.drrk.collector.job.CongestionCalculationJob;
import com.drrk.collector.publisher.congestion.CongestionMessagePublisher;
import java.time.Clock;
import java.time.Duration;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration(proxyBeanMethods = false)
@EnableScheduling
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
	CongestionCalculationJob congestionCalculationJob(
			LatestCongestionInputStore store,
			CongestionCalculator calculator,
			CongestionMessagePublisher publisher,
			Clock clock,
			@Value("${congestion.calculation.api-max-age:PT10M}") Duration apiMaxAge,
			@Value("${congestion.calculation.model-max-age:PT20S}") Duration modelMaxAge
	) {
		return new CongestionCalculationJob(store, calculator, publisher, clock, apiMaxAge, modelMaxAge);
	}
}
