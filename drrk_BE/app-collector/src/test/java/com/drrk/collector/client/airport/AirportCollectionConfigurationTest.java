package com.drrk.collector.client.airport;

import static org.assertj.core.api.Assertions.assertThat;

import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.job.AirportDataCollectionJob;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class AirportCollectionConfigurationTest {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
			.withUserConfiguration(AirportCollectionConfiguration.class)
			.withBean(LatestCongestionInputStore.class);

	@Test
	void doesNotCreateApiClientsWhenCollectionIsDisabled() {
		contextRunner
				.withPropertyValues("airport.collection.enabled=false")
				.run(context -> {
					assertThat(context).doesNotHaveBean(ArrivalStatusClient.class);
					assertThat(context).doesNotHaveBean(AirportDataCollectionJob.class);
				});
	}

	@Test
	void createsAllApiClientsWhenEnabledConfigurationIsComplete() {
		contextRunner
				.withPropertyValues(
						"airport.collection.enabled=true",
						"airport.api.num-of-rows=1000",
						"airport.api.arrival-status.url=https://airport.test/arrival",
						"airport.api.arrival-status.key=arrival-key",
						"airport.api.passenger-forecast.url=https://airport.test/passenger",
						"airport.api.passenger-forecast.key=passenger-key",
						"airport.api.railroad.url=https://airport.test/railroad",
						"airport.api.railroad.key=railroad-key"
				)
				.run(context -> {
					assertThat(context).hasNotFailed();
					assertThat(context).hasSingleBean(ArrivalStatusClient.class);
					assertThat(context).hasSingleBean(PassengerForecastClient.class);
					assertThat(context).hasSingleBean(RailroadOperationClient.class);
					assertThat(context).hasSingleBean(AirportDataCollectionJob.class);
				});
	}

	@Test
	void failsStartupWhenEnabledApiKeyIsMissing() {
		contextRunner
				.withPropertyValues(
						"airport.collection.enabled=true",
						"airport.api.num-of-rows=1000",
						"airport.api.arrival-status.url=https://airport.test/arrival",
						"airport.api.passenger-forecast.url=https://airport.test/passenger",
						"airport.api.passenger-forecast.key=passenger-key",
						"airport.api.railroad.url=https://airport.test/railroad",
						"airport.api.railroad.key=railroad-key"
				)
				.run(context -> assertThat(context).hasFailed());
	}
}
