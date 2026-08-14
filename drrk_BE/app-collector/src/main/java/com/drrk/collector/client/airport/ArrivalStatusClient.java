package com.drrk.collector.client.airport;

import com.drrk.collector.congestion.ArrivalStatusSnapshot;
import java.time.Clock;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.DefaultUriBuilderFactory;

public class ArrivalStatusClient {

	private final RestClient restClient;
	private final AirportApiProperties properties;
	private final ArrivalStatusMapper mapper;
	private final Clock clock;

	public ArrivalStatusClient(
			RestClient.Builder builder,
			AirportApiProperties properties,
			ArrivalStatusMapper mapper,
			Clock clock
	) {
		this.restClient = builder.clone()
				.uriBuilderFactory(noneEncodingFactory(properties.arrivalStatus().url()))
				.build();
		this.properties = properties;
		this.mapper = mapper;
		this.clock = clock;
	}

	public ArrivalStatusSnapshot fetch() {
		ArrivalStatusApiResponse response = restClient.get()
				.uri(uriBuilder -> uriBuilder
						.path("/getArrivalsCongestion")
						.queryParam("pageNo", 1)
						.queryParam("numOfRows", properties.numOfRows())
						.queryParam("type", "json")
						.queryParam("terno", "T1")
						.queryParam("serviceKey", AirportServiceKeys.encode(properties.arrivalStatus().key()))
						.build())
				.retrieve()
				.body(ArrivalStatusApiResponse.class);
		return mapper.map(response, clock.instant());
	}

	/**
	 * serviceKey를 직접 인코딩하므로 UriBuilder의 재인코딩을 끈다 (이중 인코딩 방지).
	 */
	private static DefaultUriBuilderFactory noneEncodingFactory(String baseUrl) {
		DefaultUriBuilderFactory factory = new DefaultUriBuilderFactory(baseUrl);
		factory.setEncodingMode(DefaultUriBuilderFactory.EncodingMode.NONE);
		return factory;
	}
}
