package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.PassengerForecastSnapshot;
import java.time.Clock;
import org.springframework.web.client.RestClient;

public class PassengerForecastClient {

	private final RestClient restClient;
	private final AirportApiProperties properties;
	private final PassengerForecastMapper mapper;
	private final Clock clock;

	public PassengerForecastClient(
			RestClient.Builder builder,
			AirportApiProperties properties,
			PassengerForecastMapper mapper,
			Clock clock
	) {
		this.restClient = builder.baseUrl(properties.passengerForecast().url()).build();
		this.properties = properties;
		this.mapper = mapper;
		this.clock = clock;
	}

	public PassengerForecastSnapshot fetch() {
		PassengerForecastApiResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/getPassgrAnncmt")
						.queryParam("pageNo", 1)
						.queryParam("numOfRows", properties.numOfRows())
						.queryParam("type", "json")
						.queryParam("selectdate", 0)
						.queryParam("serviceKey", properties.passengerForecast().key())
						.build())
				.retrieve()
				.body(PassengerForecastApiResponse.class);
		return mapper.map(response, clock.instant());
	}
}
