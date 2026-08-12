package com.drrk.collector.congestion;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.drrk.collector.job.CongestionCalculationJob;
import com.drrk.collector.publisher.congestion.CongestionMessagePublisher;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class CollectorPipelineConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(CollectorPipelineConfiguration.class)
			.withBean(CongestionMessagePublisher.class, () -> mock(CongestionMessagePublisher.class));

	@Test
	void keepsInputStoreButDoesNotScheduleCalculationWhenDisabled() {
		contextRunner
				.withPropertyValues("congestion.calculation.enabled=false")
				.run(context -> {
					assertThat(context).hasSingleBean(LatestCongestionInputStore.class);
					assertThat(context).doesNotHaveBean(CongestionCalculationJob.class);
				});
	}

	@Test
	void createsCalculationJobWhenEnabled() {
		contextRunner
				.withPropertyValues("congestion.calculation.enabled=true")
				.run(context -> assertThat(context).hasSingleBean(CongestionCalculationJob.class));
	}
}
