package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.job.AirportDataCollectionJob;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "airport.collection", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AirportApiProperties.class)
public class AirportCollectionConfiguration {

	@Bean
	@ConditionalOnMissingBean
	Clock clock() {
		return Clock.systemUTC();
	}

	@Bean
	ArrivalStatusMapper arrivalStatusMapper() {
		return new ArrivalStatusMapper();
	}

	@Bean
	PassengerForecastMapper passengerForecastMapper() {
		return new PassengerForecastMapper();
	}

	@Bean
	RailroadOperationMapper railroadOperationMapper() {
		return new RailroadOperationMapper();
	}

	@Bean
	ArrivalStatusClient arrivalStatusClient(
			AirportApiProperties properties,
			ArrivalStatusMapper mapper,
			Clock clock
	) {
		return new ArrivalStatusClient(restClientBuilder(), properties, mapper, clock);
	}

	@Bean
	PassengerForecastClient passengerForecastClient(
			AirportApiProperties properties,
			PassengerForecastMapper mapper,
			Clock clock
	) {
		return new PassengerForecastClient(restClientBuilder(), properties, mapper, clock);
	}

	@Bean
	RailroadOperationClient railroadOperationClient(
			AirportApiProperties properties,
			RailroadOperationMapper mapper,
			Clock clock
	) {
		return new RailroadOperationClient(restClientBuilder(), properties, mapper, clock);
	}

	@Bean
	AirportDataCollectionJob airportDataCollectionJob(
			ArrivalStatusClient arrivalStatusClient,
			PassengerForecastClient passengerForecastClient,
			RailroadOperationClient railroadOperationClient,
			LatestCongestionInputStore store
	) {
		return new AirportDataCollectionJob(arrivalStatusClient, passengerForecastClient, railroadOperationClient, store);
	}

	private RestClient.Builder restClientBuilder() {
		SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
		requestFactory.setConnectTimeout(Duration.ofSeconds(2));
		requestFactory.setReadTimeout(Duration.ofSeconds(5));
		return RestClient.builder().requestFactory(requestFactory);
	}
}
