package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.LatestCongestionInputStore;
import com.drrk.collector.job.AirportDataCollectionJob;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "airport.collection", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(AirportApiProperties.class)
public class AirportCollectionConfiguration {

	private static final Logger log = LoggerFactory.getLogger(AirportCollectionConfiguration.class);

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
		return RestClient.builder()
				.requestFactory(requestFactory)
				// 공공데이터포털은 기본 Java User-Agent를 차단하는 경우가 있어 명시한다.
				.defaultHeader(HttpHeaders.USER_AGENT, "drrk-collector/1.0")
				.defaultHeader(HttpHeaders.ACCEPT, "application/json,text/xml;q=0.9,*/*;q=0.8")
				.defaultStatusHandler(HttpStatusCode::isError, (request, response) -> {
					String body = new String(
							response.getBody().readAllBytes(), StandardCharsets.UTF_8);
					String preview = body.length() > 500 ? body.substring(0, 500) : body;
					log.warn("[AIRPORT API ERROR] status={} uri={} body={}",
							response.getStatusCode(), stripKey(request.getURI().toString()), preview);
					throw new AirportApiResponseException(
							String.valueOf(response.getStatusCode().value()), preview);
				});
	}

	/**
	 * 로그에 serviceKey가 남지 않도록 마스킹한다.
	 */
	private static String stripKey(String uri) {
		return uri.replaceAll("serviceKey=[^&]*", "serviceKey=***");
	}
}
