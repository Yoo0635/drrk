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

	@Test
	void createsMovingWalkwayCalculatorWhenAllExistingRouteConstantsAreConfigured() {
		contextRunner
				.withPropertyValues(
						"congestion.moving-walkway.enabled=true",
						"congestion.moving-walkway.sensor-space-id=desk01",
						"congestion.moving-walkway.carriers-per-passenger=0.1",
						"congestion.moving-walkway.route-b.split=0.5",
						"congestion.moving-walkway.route-b.retention-length-seconds=100",
						"congestion.moving-walkway.route-b.walkway-arrival-offset-seconds=20",
						"congestion.moving-walkway.route-b.available-passage-time-seconds=10",
						"congestion.moving-walkway.route-b.congested-passage-time-seconds=30",
						"congestion.moving-walkway.route-b.remaining-travel-time-seconds=50",
						"congestion.moving-walkway.route-c.split=0.2",
						"congestion.moving-walkway.route-c.retention-length-seconds=100",
						"congestion.moving-walkway.route-c.walkway-arrival-offset-seconds=30",
						"congestion.moving-walkway.route-c.available-passage-time-seconds=20",
						"congestion.moving-walkway.route-c.congested-passage-time-seconds=40",
						"congestion.moving-walkway.route-c.remaining-travel-time-seconds=20"
				)
				.run(context -> assertThat(context).hasSingleBean(MovingWalkwayCongestionCalculator.class));
	}
}
